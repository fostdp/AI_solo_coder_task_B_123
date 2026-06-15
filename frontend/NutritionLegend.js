/**
 * NutritionLegend - 营养风险图例组件
 * 
 * 功能：
 * - 四色风险等级图例（低/中/高/严重）
 * - 支持水平排列和垂直排列
 * - 可交互点击筛选
 * - 风险分布统计条
 * 
 * 用法：
 * const legend = new NutritionLegend(containerElement, {
 *     layout: 'horizontal',
 *     interactive: true,
 *     onLevelClick: (level) => { ... }
 * });
 * legend.setData(riskCounts);
 * 
 * riskCounts 格式: { LOW: 100, MEDIUM: 30, HIGH: 10, CRITICAL: 5 }
 */

class NutritionLegend {
    constructor(container, options = {}) {
        if (!container) throw new Error('NutritionLegend: container element is required');

        this.container = container;
        this.options = Object.assign({
            layout: 'horizontal',
            interactive: false,
            showCounts: true,
            showDistribution: true,
            onLevelClick: null
        }, options);

        this.RISK_LEVELS = [
            { key: 'LOW', label: '低风险', color: '#81c784' },
            { key: 'MEDIUM', label: '中风险', color: '#ffd54f' },
            { key: 'HIGH', label: '高风险', color: '#ff8a65' },
            { key: 'CRITICAL', label: '严重', color: '#ef5350' }
        ];

        this.data = {};
        this.selectedLevel = null;
        this._render();
    }

    setData(data) {
        this.data = data || {};
        this._render();
    }

    setSelectedLevel(level) {
        this.selectedLevel = level;
        this._updateSelection();
    }

    getSelectedLevel() {
        return this.selectedLevel;
    }

    getColor(level) {
        const found = this.RISK_LEVELS.find(l => l.key === level);
        return found ? found.color : '#999';
    }

    _render() {
        this.container.innerHTML = '';
        this.container.className = 'nutrition-legend';

        if (this.options.layout === 'vertical') {
            this.container.style.display = 'flex';
            this.container.style.flexDirection = 'column';
            this.container.style.gap = '8px';
        } else {
            this.container.style.display = 'flex';
            this.container.style.flexWrap = 'wrap';
            this.container.style.gap = '8px';
        }

        this.items = [];

        this.RISK_LEVELS.forEach(level => {
            const item = document.createElement('div');
            item.className = 'legend-item';
            item.dataset.level = level.key;
            item.style.cssText = `
                display: flex;
                align-items: center;
                gap: 6px;
                background: rgba(0, 0, 0, 0.6);
                padding: 6px 12px;
                border-radius: 4px;
                font-size: 12px;
                border: 1px solid rgba(212, 165, 116, 0.3);
                cursor: ${this.options.interactive ? 'pointer' : 'default'};
                transition: all 0.2s;
            `;

            const dot = document.createElement('div');
            dot.className = 'legend-dot';
            dot.style.cssText = `
                width: 12px;
                height: 12px;
                border-radius: 50%;
                background: ${level.color};
                flex-shrink: 0;
            `;

            const label = document.createElement('span');
            label.textContent = level.label;
            label.style.color = '#e8e8e8';

            item.appendChild(dot);
            item.appendChild(label);

            if (this.options.showCounts) {
                const count = this.data[level.key] || 0;
                const countSpan = document.createElement('span');
                countSpan.textContent = `(${count})`;
                countSpan.style.cssText = 'color:#a9a9a9; margin-left:2px;';
                item.appendChild(countSpan);
            }

            if (this.options.interactive) {
                item.addEventListener('click', () => {
                    if (this.selectedLevel === level.key) {
                        this.selectedLevel = null;
                    } else {
                        this.selectedLevel = level.key;
                    }
                    this._updateSelection();
                    if (this.options.onLevelClick) {
                        this.options.onLevelClick(this.selectedLevel);
                    }
                });
                item.addEventListener('mouseenter', () => {
                    item.style.background = 'rgba(212, 165, 116, 0.2)';
                });
                item.addEventListener('mouseleave', () => {
                    if (this.selectedLevel !== level.key) {
                        item.style.background = 'rgba(0, 0, 0, 0.6)';
                    }
                });
            }

            this.container.appendChild(item);
            this.items.push(item);
        });

        if (this.options.showDistribution && this._hasData()) {
            const distBar = this._createDistributionBar();
            if (this.options.layout === 'vertical') {
                this.container.appendChild(distBar);
            }
        }
    }

    _hasData() {
        return Object.values(this.data).some(v => v > 0);
    }

    _createDistributionBar() {
        const container = document.createElement('div');
        container.className = 'risk-distribution';
        container.style.cssText = `
            display: flex;
            height: 24px;
            border-radius: 4px;
            overflow: hidden;
            margin-top: 4px;
        `;

        const total = Object.values(this.data).reduce((a, b) => a + b, 0);

        this.RISK_LEVELS.forEach(level => {
            const count = this.data[level.key] || 0;
            const pct = total > 0 ? (count / total) * 100 : 0;
            if (pct < 0.5) return;

            const seg = document.createElement('div');
            seg.style.cssText = `
                flex: ${pct};
                background: ${level.color};
                display: flex;
                align-items: center;
                justify-content: center;
                font-size: 10px;
                color: #1a1a2e;
                font-weight: bold;
            `;
            if (pct > 8) {
                seg.textContent = count;
            }

            container.appendChild(seg);
        });

        return container;
    }

    _updateSelection() {
        if (!this.items) return;
        this.items.forEach(item => {
            const level = item.dataset.level;
            if (this.selectedLevel && this.selectedLevel === level) {
                item.style.background = 'rgba(212, 165, 116, 0.3)';
                item.style.borderColor = '#d4a574';
            } else {
                item.style.background = 'rgba(0, 0, 0, 0.6)';
                item.style.borderColor = 'rgba(212, 165, 116, 0.3)';
            }
        });
    }

    getDistributionPercent() {
        const total = Object.values(this.data).reduce((a, b) => a + b, 0);
        const result = {};
        this.RISK_LEVELS.forEach(level => {
            const count = this.data[level.key] || 0;
            result[level.key] = total > 0 ? (count / total) * 100 : 0;
        });
        return result;
    }
}
