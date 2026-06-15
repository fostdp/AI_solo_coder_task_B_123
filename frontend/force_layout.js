/**
 * Force-directed碰撞避免布局模块
 * 
 * 根因: 士兵圆点在兵营平面图上重叠严重，静态jitter不足以解决密集分布问题
 * 修复: 使用D3.js forceSimulation实现力导向布局，包含碰撞检测力
 *        士兵节点从原始坐标出发，通过物理模拟自动避开重叠
 */

class ForceLayoutManager {
    constructor(canvas, options = {}) {
        this.canvas = canvas;
        this.ctx = canvas.getContext('2d');
        this.padding = options.padding || 60;
        this.nodeRadius = options.nodeRadius || 6;
        this.collisionPadding = options.collisionPadding || 3;
        this.chargeStrength = options.chargeStrength || -15;
        this.alphaDecay = options.alphaDecay || 0.04;
        this.alphaMin = options.alphaMin || 0.005;
        this.velocityDecay = options.velocityDecay || 0.4;

        this.simulation = null;
        this.nodes = [];
        this.soldierPositions = [];
        this.drawWidth = 0;
        this.drawHeight = 0;
        this.barracks = null;

        this.RISK_COLORS = {
            LOW: '#81c784',
            MEDIUM: '#ffd54f',
            HIGH: '#ff8a65',
            CRITICAL: '#ef5350'
        };
    }

    init(soldiers, barracks, allBarracks) {
        this.barracks = barracks;
        const rect = this.canvas.parentElement.getBoundingClientRect();
        this.canvas.width = rect.width;
        this.canvas.height = rect.height;
        this.drawWidth = this.canvas.width - this.padding * 2;
        this.drawHeight = this.canvas.height - this.padding * 2;

        this.nodes = soldiers.map((soldier, idx) => {
            const baseX = (soldier.positionX || 50) / 200;
            const baseY = (soldier.positionY || 50) / 200;
            const jitterX = Math.abs((Math.sin(idx * 12.9898) * 43758.5453) % 1);
            const jitterY = Math.abs((Math.sin(idx * 78.233) * 43758.5453) % 1);

            const fx = this.padding + this.drawWidth * (0.05 + baseX * 0.9 + jitterX * 0.02);
            const fy = this.padding + this.drawHeight * (0.05 + baseY * 0.9 + jitterY * 0.02);

            const riskScore = soldier.overallRiskScore || 0;
            const radius = this.nodeRadius * (0.8 + riskScore * 1.2);

            return {
                id: idx,
                x: fx,
                y: fy,
                fx: null,
                fy: null,
                radius: radius,
                soldier: soldier,
                color: this.RISK_COLORS[soldier.riskLevel] || this.RISK_COLORS.LOW,
                riskScore: riskScore
            };
        });

        this._buildSimulation();
    }

    _buildSimulation() {
        if (this.simulation) {
            this.simulation.stop();
        }

        this.simulation = d3.forceSimulation(this.nodes)
            .force('collision', d3.forceCollide()
                .radius(d => d.radius + this.collisionPadding)
                .strength(1.0)
                .iterations(3))
            .force('x', d3.forceX(d => {
                const baseX = (d.soldier.positionX || 50) / 200;
                return this.padding + this.drawWidth * (0.05 + baseX * 0.9);
            }).strength(0.15))
            .force('y', d3.forceY(d => {
                const baseY = (d.soldier.positionY || 50) / 200;
                return this.padding + this.drawHeight * (0.05 + baseY * 0.9);
            }).strength(0.15))
            .force('charge', d3.forceManyBody()
                .strength(this.chargeStrength)
                .distanceMax(60))
            .force('bounding', this._boundingForce())
            .alphaDecay(this.alphaDecay)
            .alphaMin(this.alphaMin)
            .velocityDecay(this.velocityDecay)
            .on('tick', () => this._render());
    }

    _boundingForce() {
        const self = this;
        return function() {
            for (let i = 0; i < self.nodes.length; i++) {
                const node = self.nodes[i];
                const r = node.radius;
                const minX = self.padding + r + 4;
                const maxX = self.padding + self.drawWidth - r - 4;
                const minY = self.padding + r + 4;
                const maxY = self.padding + self.drawHeight - r - 4;

                if (node.x < minX) { node.x = minX; node.vx = Math.abs(node.vx) * 0.3; }
                if (node.x > maxX) { node.x = maxX; node.vx = -Math.abs(node.vx) * 0.3; }
                if (node.y < minY) { node.y = minY; node.vy = Math.abs(node.vy) * 0.3; }
                if (node.y > maxY) { node.y = maxY; node.vy = -Math.abs(node.vy) * 0.3; }
            }
        };
    }

    _render() {
        const ctx = this.ctx;
        ctx.clearRect(0, 0, this.canvas.width, this.canvas.height);

        this._drawBackground(ctx);
        this._drawGrid(ctx);
        this._drawAreas(ctx);
        this._drawNodes(ctx);

        this.soldierPositions = this.nodes.map(n => ({
            x: n.x,
            y: n.y,
            soldier: n.soldier,
            radius: n.radius
        }));
        this.canvas._soldierPositions = this.soldierPositions;
    }

    _drawBackground(ctx) {
        ctx.fillStyle = 'rgba(139, 69, 19, 0.15)';
        ctx.strokeStyle = '#d4a574';
        ctx.lineWidth = 3;
        this._roundRect(ctx, this.padding, this.padding, this.drawWidth, this.drawHeight, 12, true, true);

        if (this.barracks) {
            ctx.fillStyle = '#d4a574';
            ctx.font = 'bold 16px Microsoft YaHei';
            ctx.textAlign = 'center';
            ctx.fillText(this.barracks.name, this.canvas.width / 2, this.padding - 20);
        }
    }

    _drawGrid(ctx) {
        ctx.strokeStyle = 'rgba(212, 165, 116, 0.15)';
        ctx.lineWidth = 1;
        const gridSize = 40;
        for (let x = this.padding; x <= this.padding + this.drawWidth; x += gridSize) {
            ctx.beginPath();
            ctx.moveTo(x, this.padding);
            ctx.lineTo(x, this.padding + this.drawHeight);
            ctx.stroke();
        }
        for (let y = this.padding; y <= this.padding + this.drawHeight; y += gridSize) {
            ctx.beginPath();
            ctx.moveTo(this.padding, y);
            ctx.lineTo(this.padding + this.drawWidth, y);
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

        areas.forEach(area => {
            const ax = this.padding + this.drawWidth * area.x;
            const ay = this.padding + this.drawHeight * area.y;
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

    getSoldierAt(x, y) {
        const positions = this.soldierPositions;
        for (const pos of positions) {
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

    reheat() {
        if (this.simulation) {
            this.simulation.alpha(0.3).restart();
        }
    }
}
