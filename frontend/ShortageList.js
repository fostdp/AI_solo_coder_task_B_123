class ShortageList {
    constructor(supplyApi, chartCanvasId, summaryId, rulesId) {
        this.supplyApi = supplyApi;
        this.chartCanvasId = chartCanvasId;
        this.summaryId = summaryId;
        this.rulesId = rulesId;
        this.chart = null;
        this.btnClickHandler = null;
    }

    async load() {
        try {
            const [rulesRes, topRes] = await Promise.all([
                fetch(`${this.supplyApi}/v1/association-rules/significant`),
                fetch(`${this.supplyApi}/v1/supply-analysis/top-deficits`)
            ]);
            const rules = await rulesRes.json();
            const topDeficits = await topRes.json();

            const summary = document.getElementById(this.summaryId);
            const sigRules = Array.isArray(rules) ? rules.filter(r => r.isSignificant !== false) : [];
            summary.innerHTML = `
                <div><strong>显著关联规则:</strong> ${sigRules.length}条</div>
                <div><strong>分析周期:</strong> 最近30天</div>
                <div><strong>最小Lift阈值:</strong> 1.5</div>
            `;

            const ctx = document.getElementById(this.chartCanvasId).getContext('2d');
            if (this.chart) this.chart.destroy();
            const labels = (topDeficits || []).slice(0, 5).map(d => d.foodCategory || d.foodName || '');
            const data = (topDeficits || []).slice(0, 5).map(d => (d.avgDeficitRate || d.deficitRate || 0) * 100);
            this.chart = new Chart(ctx, {
                type: 'bar',
                data: {
                    labels,
                    datasets: [{
                        label: '平均短缺率 (%)',
                        data,
                        backgroundColor: ['#fee2e2', '#fecaca', '#fed7aa', '#fde68a', '#fef08a'].reverse()
                    }]
                },
                options: {
                    indexAxis: 'y',
                    responsive: true,
                    maintainAspectRatio: false,
                    plugins: { legend: { display: false } },
                    scales: { x: { beginAtZero: true, max: 100, ticks: { callback: v => v+'%' } } }
                }
            });

            const rulesEl = document.getElementById(this.rulesId);
            rulesEl.innerHTML = (sigRules.slice(0, 8)).map((r, i) => `
                <div style="padding: 6px; border-bottom: 1px solid #e5e7eb; background: ${i<3?'#fffbeb':'transparent'};">
                    <div style="font-weight: 500;">${r.ruleDescription || r.antecedentItems + ' → ' + r.consequentItems}</div>
                    <div style="color: #6b7280; font-size: 11px;">
                        Support: ${((r.support||0)*100).toFixed(1)}% | 
                        Confidence: ${((r.confidence||0)*100).toFixed(1)}% | 
                        Lift: <strong style="color:#dc2626;">${(r.lift||0).toFixed(2)}</strong>
                    </div>
                </div>
            `).join('');

        } catch (e) {
            document.getElementById(this.summaryId).innerHTML = '<p style="color: #ef4444;">补给服务连接失败 (8087未启动)</p>';
        }
    }

    async runAnalysis() {
        try {
            await fetch(`${this.supplyApi}/v1/supply-analysis/run`, { method: 'POST' });
            setTimeout(() => this.load(), 500);
        } catch (e) {
            console.error('补给分析失败:', e);
        }
    }

    bindButton(btnId) {
        const btn = document.getElementById(btnId);
        if (!btn) return;
        this.btnClickHandler = async (e) => {
            btn.disabled = true;
            btn.textContent = '分析中...';
            try {
                await this.runAnalysis();
            } finally {
                btn.disabled = false;
                btn.textContent = '分析最近30天';
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
            const btn = document.getElementById('runAprioriBtn');
            if (btn) btn.removeEventListener('click', this.btnClickHandler);
            this.btnClickHandler = null;
        }
    }
}
