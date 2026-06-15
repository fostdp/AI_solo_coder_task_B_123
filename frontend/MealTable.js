class MealTable {
    constructor(apiBase, mealPlannerApi, chartCanvasId, summaryId, detailId) {
        this.apiBase = apiBase;
        this.mealPlannerApi = mealPlannerApi;
        this.chartCanvasId = chartCanvasId;
        this.summaryId = summaryId;
        this.detailId = detailId;
        this.chart = null;
        this.btnClickHandler = null;
    }

    async load() {
        try {
            const res = await fetch(`${this.mealPlannerApi}/v1/meal-plans?pageSize=1`);
            const data = await res.json();
            const summary = document.getElementById(this.summaryId);
            if (data && data.length > 0) {
                const plan = data[0];
                summary.innerHTML = `
                    <div><strong>计划名称:</strong> ${plan.planName || '本周食谱'}</div>
                    <div><strong>周期:</strong> ${plan.startDate || '-'} ~ ${plan.endDate || '-'}</div>
                    <div><strong>目标人数:</strong> ${plan.targetSoldierCount || 0}人</div>
                    <div><strong>总成本:</strong> ¥${(plan.totalCost || 0).toFixed(2)}</div>
                    <div><strong>求解状态:</strong> ${plan.solverStatus || '-'}</div>
                `;
                this._renderChart(plan);
                this._renderDetail(plan);
            } else {
                summary.innerHTML = '<p>暂无食谱计划，点击右上角按钮生成</p>';
            }
        } catch (e) {
            document.getElementById(this.summaryId).innerHTML = '<p style="color: #ef4444;">膳食服务连接失败 (8084未启动)</p>';
        }
    }

    _renderChart(plan) {
        const ctx = document.getElementById(this.chartCanvasId).getContext('2d');
        if (this.chart) this.chart.destroy();
        this.chart = new Chart(ctx, {
            type: 'bar',
            data: {
                labels: ['蛋白质(g)', '脂肪(g)', '维C(mg)', '热量(kcal)'],
                datasets: [
                    {
                        label: '实际达成',
                        data: [
                            plan.dailyProteinG || 0,
                            plan.dailyFatG || 0,
                            plan.dailyVitaminCMg || 0,
                            plan.dailyCalorieKcal || 0
                        ],
                        backgroundColor: 'rgba(59, 130, 246, 0.7)'
                    },
                    {
                        label: '目标下限',
                        data: [80, 50, 100, 2500],
                        backgroundColor: 'rgba(34, 197, 94, 0.5)'
                    }
                ]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                plugins: { legend: { position: 'bottom', labels: { font: { size: 10 } } } },
                scales: { y: { beginAtZero: true } }
            }
        });
    }

    _renderDetail(plan) {
        const el = document.getElementById(this.detailId);
        if (plan.items && plan.items.length > 0) {
            const grouped = {};
            plan.items.forEach(it => {
                const key = `第${it.dayOfWeek}天-${it.mealType}`;
                if (!grouped[key]) grouped[key] = [];
                grouped[key].push(`${it.foodName} ${it.quantityGrams || 0}g`);
            });
            el.innerHTML = Object.entries(grouped).map(([k, v]) =>
                `<div style="margin: 4px 0;"><strong>${k}:</strong> ${v.join('、')}</div>`
            ).join('');
        }
    }

    async generate(barracksId) {
        try {
            if (!barracksId) {
                const barracksRes = await fetch(`${this.apiBase}/v1/barracks`);
                const barracks = await barracksRes.json();
                barracksId = barracks.length > 0 ? barracks[0].id : 1;
            }
            await fetch(`${this.mealPlannerApi}/v1/meal-plans/generate`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ barracksId, startDate: new Date().toISOString().slice(0,10), soldierCount: 40 })
            });
            setTimeout(() => this.load(), 500);
        } catch (e) {
            console.error('生成食谱失败:', e);
        }
    }

    bindButton(btnId) {
        const btn = document.getElementById(btnId);
        if (!btn) return;
        this.btnClickHandler = async (e) => {
            btn.disabled = true;
            btn.textContent = '生成中...';
            try {
                await this.generate();
            } finally {
                btn.disabled = false;
                btn.textContent = '生成本周食谱';
            }
        };
        btn.addEventListener('click', this.btnClickHandler);
    }

    destroy() {
        if (this.chart) {
            this.chart.destroy();
            this.chart = null;
        }
        if (this.btnClickHandler) {
            const btn = document.getElementById('generateMealPlanBtn');
            if (btn) btn.removeEventListener('click', this.btnClickHandler);
            this.btnClickHandler = null;
        }
    }
}
