/**
 * BarrackMap - 兵营平面图组件
 * 
 * 功能：
 * - Canvas绘制兵营平面图（营房、食堂、校场、指挥所）
 * - D3.js Force-directed 力导向布局，士兵圆点碰撞避免
 * - 点击士兵弹出详情面板
 * - 支持切换兵营、数据刷新
 * 
 * 用法：
 * const map = new BarrackMap(canvasElement, {
 *     padding: 60,
 *     onSoldierClick: (soldier) => { ... }
 * });
 * map.setData(soldiers, barracks);
 * map.refresh();
 */

class BarrackMap {
    constructor(canvas, options = {}) {
        if (!canvas) throw new Error('BarrackMap: canvas element is required');

        this.canvas = canvas;
        this.ctx = canvas.getContext('2d');
        this.options = Object.assign({
            padding: 60,
            nodeRadius: 6,
            collisionPadding: 3,
            chargeStrength: -15,
            alphaDecay: 0.04,
            alphaMin: 0.005,
            velocityDecay: 0.4,
            onSoldierClick: null,
            onSoldierHover: null
        }, options);

        this.soldiers = [];
        this.barracks = null;
        this.simulation = null;
        this.nodes = [];
        this.soldierPositions = [];
        this.drawWidth = 0;
        this.drawHeight = 0;

        this.RISK_COLORS = {
            LOW: '#81c784',
            MEDIUM: '#ffd54f',
            HIGH: '#ff8a65',
            CRITICAL: '#ef5350'
        };

        this._bindEvents();
    }

    _bindEvents() {
        this.canvas.addEventListener('click', (e) => {
            const pos = this._getMousePos(e);
            const soldier = this.getSoldierAt(pos.x, pos.y);
            if (soldier && this.options.onSoldierClick) {
                this.options.onSoldierClick(soldier);
            }
        });

        this.canvas.addEventListener('mousemove', (e) => {
            const pos = this._getMousePos(e);
            const isHover = this.isHovering(pos.x, pos.y);
            this.canvas.style.cursor = isHover ? 'pointer' : 'default';
            if (this.options.onSoldierHover) {
                const soldier = this.getSoldierAt(pos.x, pos.y);
                this.options.onSoldierHover(soldier);
            }
        });
    }

    _getMousePos(e) {
        const rect = this.canvas.getBoundingClientRect();
        return {
            x: e.clientX - rect.left,
            y: e.clientY - rect.top
        };
    }

    setData(soldiers, barracks) {
        this.soldiers = soldiers || [];
        this.barracks = barracks || null;
        this._resize();
        this._buildNodes();
        this._buildSimulation();
    }

    refresh() {
        if (this.simulation) {
            this.simulation.alpha(0.3).restart();
        }
    }

    getSoldierAt(x, y) {
        for (const pos of this.soldierPositions) {
            const dist = Math.sqrt((x - pos.x) ** 2 + (y - pos.y) ** 2);
            if (dist <= pos.radius + 4) {
                return pos.soldier;
            }
        }
        return null;
    }

    isHovering(x, y) {
        for (const pos of this.soldierPositions) {
            const dist = Math.sqrt((x - pos.x) ** 2 + (y - pos.y) ** 2);
            if (dist <= pos.radius + 4) {
                return true;
            }
        }
        return false;
    }

    stop() {
        if (this.simulation) {
            this.simulation.stop();
        }
    }

    _resize() {
        const rect = this.canvas.parentElement.getBoundingClientRect();
        this.canvas.width = rect.width;
        this.canvas.height = rect.height;
        this.drawWidth = this.canvas.width - this.options.padding * 2;
        this.drawHeight = this.canvas.height - this.options.padding * 2;
    }

    _buildNodes() {
        const p = this.options;
        this.nodes = this.soldiers.map((soldier, idx) => {
            const baseX = (soldier.positionX || 50) / 200;
            const baseY = (soldier.positionY || 50) / 200;
            const jitterX = Math.abs((Math.sin(idx * 12.9898) * 43758.5453) % 1);
            const jitterY = Math.abs((Math.sin(idx * 78.233) * 43758.5453) % 1);

            const fx = p.padding + this.drawWidth * (0.05 + baseX * 0.9 + jitterX * 0.02);
            const fy = p.padding + this.drawHeight * (0.05 + baseY * 0.9 + jitterY * 0.02);

            const riskScore = soldier.overallRiskScore || 0;
            const radius = p.nodeRadius * (0.8 + riskScore * 1.2);

            return {
                id: idx,
                x: fx,
                y: fy,
                radius: radius,
                soldier: soldier,
                color: this.RISK_COLORS[soldier.riskLevel] || this.RISK_COLORS.LOW,
                riskScore: riskScore
            };
        });
    }

    _buildSimulation() {
        if (this.simulation) {
            this.simulation.stop();
        }

        const p = this.options;
        const self = this;

        this.simulation = d3.forceSimulation(this.nodes)
            .force('collision', d3.forceCollide()
                .radius(d => d.radius + p.collisionPadding)
                .strength(1.0)
                .iterations(3))
            .force('x', d3.forceX(d => {
                const baseX = (d.soldier.positionX || 50) / 200;
                return p.padding + self.drawWidth * (0.05 + baseX * 0.9);
            }).strength(0.15))
            .force('y', d3.forceY(d => {
                const baseY = (d.soldier.positionY || 50) / 200;
                return p.padding + self.drawHeight * (0.05 + baseY * 0.9);
            }).strength(0.15))
            .force('charge', d3.forceManyBody()
                .strength(p.chargeStrength)
                .distanceMax(60))
            .force('bounding', () => self._boundingForce())
            .alphaDecay(p.alphaDecay)
            .alphaMin(p.alphaMin)
            .velocityDecay(p.velocityDecay)
            .on('tick', () => this._render());
    }

    _boundingForce() {
        const p = this.options;
        for (let i = 0; i < this.nodes.length; i++) {
            const node = this.nodes[i];
            const r = node.radius;
            const minX = p.padding + r + 4;
            const maxX = p.padding + this.drawWidth - r - 4;
            const minY = p.padding + r + 4;
            const maxY = p.padding + this.drawHeight - r - 4;

            if (node.x < minX) { node.x = minX; node.vx = Math.abs(node.vx) * 0.3; }
            if (node.x > maxX) { node.x = maxX; node.vx = -Math.abs(node.vx) * 0.3; }
            if (node.y < minY) { node.y = minY; node.vy = Math.abs(node.vy) * 0.3; }
            if (node.y > maxY) { node.y = maxY; node.vy = -Math.abs(node.vy) * 0.3; }
        }
    }

    _render() {
        const ctx = this.ctx;
        ctx.clearRect(0, 0, this.canvas.width, this.canvas.height);

        this._drawBackground(ctx);
        this._drawGrid(ctx);
        this._drawAreas(ctx);
        this._drawNodes(ctx);

        this.soldierPositions = this.nodes.map(n => ({
            x: n.x, y: n.y, soldier: n.soldier, radius: n.radius
        }));
        this.canvas._soldierPositions = this.soldierPositions;
    }

    _drawBackground(ctx) {
        const p = this.options;
        ctx.fillStyle = 'rgba(139, 69, 19, 0.15)';
        ctx.strokeStyle = '#d4a574';
        ctx.lineWidth = 3;
        this._roundRect(ctx, p.padding, p.padding, this.drawWidth, this.drawHeight, 12, true, true);

        if (this.barracks) {
            ctx.fillStyle = '#d4a574';
            ctx.font = 'bold 16px Microsoft YaHei';
            ctx.textAlign = 'center';
            ctx.fillText(this.barracks.name, this.canvas.width / 2, p.padding - 20);
        }
    }

    _drawGrid(ctx) {
        const p = this.options;
        ctx.strokeStyle = 'rgba(212, 165, 116, 0.15)';
        ctx.lineWidth = 1;
        const gridSize = 40;
        for (let x = p.padding; x <= p.padding + this.drawWidth; x += gridSize) {
            ctx.beginPath();
            ctx.moveTo(x, p.padding);
            ctx.lineTo(x, p.padding + this.drawHeight);
            ctx.stroke();
        }
        for (let y = p.padding; y <= p.padding + this.drawHeight; y += gridSize) {
            ctx.beginPath();
            ctx.moveTo(p.padding, y);
            ctx.lineTo(p.padding + this.drawWidth, y);
            ctx.stroke();
        }
    }

    _drawAreas(ctx) {
        const areas = [
            { name: '营房A', x: 0.1, y: 0.1, w: 0.35, h: 0.35 },
            { name: '营房B', x: 0.55, y: 0.1, w: 0.35, h: 0.35 },
            { name: '食堂', x: 0.1, y: 0.55, w: 0.35, h: 0.3 },
            { name: '校场', x: 0.55, y: 0.55, w: 0.35, h: 0.3 },
            { name: '指挥所', x: 0.4, y: 0.4, w: 0.2, h: 0.15 }
        ];

        const p = this.options;
        areas.forEach(area => {
            const ax = p.padding + this.drawWidth * area.x;
            const ay = p.padding + this.drawHeight * area.y;
            const aw = this.drawWidth * area.w;
            const ah = this.drawHeight * area.h;

            ctx.fillStyle = 'rgba(212, 165, 116, 0.08)';
            ctx.strokeStyle = 'rgba(212, 165, 116, 0.4)';
            ctx.lineWidth = 1;
            this._roundRect(ctx, ax, ay, aw, ah, 6, true, true);

            ctx.fillStyle = 'rgba(212, 165, 116, 0.7)';
            ctx.font = '11px Microsoft YaHei';
            ctx.textAlign = 'center';
            ctx.fillText(area.name, ax + aw / 2, ay + 16);
        });
    }

    _drawNodes(ctx) {
        for (const node of this.nodes) {
            const px = node.x;
            const py = node.y;
            const color = node.color;
            const radius = node.radius;

            const gradient = ctx.createRadialGradient(px, py, 0, px, py, radius * 2);
            gradient.addColorStop(0, color);
            gradient.addColorStop(1, 'rgba(0,0,0,0)');
            ctx.fillStyle = gradient;
            ctx.beginPath();
            ctx.arc(px, py, radius * 2, 0, Math.PI * 2);
            ctx.fill();

            ctx.fillStyle = color;
            ctx.strokeStyle = 'rgba(255, 255, 255, 0.3)';
            ctx.lineWidth = 1;
            ctx.beginPath();
            ctx.arc(px, py, radius, 0, Math.PI * 2);
            ctx.fill();
            ctx.stroke();
        }
    }

    _roundRect(ctx, x, y, w, h, r, fill, stroke) {
        ctx.beginPath();
        ctx.moveTo(x + r, y);
        ctx.lineTo(x + w - r, y);
        ctx.quadraticCurveTo(x + w, y, x + w, y + r);
        ctx.lineTo(x + w, y + h - r);
        ctx.quadraticCurveTo(x + w, y + h, x + w - r, y + h);
        ctx.lineTo(x + r, y + h);
        ctx.quadraticCurveTo(x, y + h, x, y + h - r);
        ctx.lineTo(x, y + r);
        ctx.quadraticCurveTo(x, y, x + r, y);
        ctx.closePath();
        if (fill) ctx.fill();
        if (stroke) ctx.stroke();
    }

    getStats() {
        const stats = {
            LOW: 0, MEDIUM: 0, HIGH: 0, CRITICAL: 0, total: this.soldiers.length
        };
        for (const s of this.soldiers) {
            const level = s.riskLevel || 'LOW';
            stats[level] = (stats[level] || 0) + 1;
        }
        return stats;
    }
}
