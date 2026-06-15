class InterventionCard {
    constructor(interventionApi, chartCanvasId, summaryId, listId) {
        this.interventionApi = interventionApi;
        this.chartCanvasId = chartCanvasId;
        this.summaryId = summaryId;
        this.listId = listId;
        this.chart = null;
        this.btnClickHandler = null;
    }

    async load() {
        try {
            const res = await fetch(`${this.interventionApi}/v1/intervention-recommendations?pageSize=50`);
            const data = await res.json();
            const summary = document.getElementById(this.summaryId);
            const recs = Array.isArray(data) ? data : (data.content || data.items || []);

            const pending = recs.filter(r => r.status === 'PENDING' || !r.status).length;
            const totalCost = recs.reduce((s, r) => s + (r.estimatedCostTotal || 0), 0);

            const riskCounts = { CRITICAL: 0, HIGH: 0, MEDIUM: 0, LOW: 0 };
            recs.forEach(r => { if (r.riskLevel) riskCounts[r.riskLevel] = (riskCounts[r.riskLevel] || 0) + 1; });

            summary.innerHTML = `
                <div><strong>推荐总数:</strong> ${recs.length}条</div>
                <div><strong>待执行:</strong> ${pending}条</div>
                <div><strong>预计总费用:</strong> ¥${totalCost.toFixed(2)}</div>
            `;

            const ctx = document.getElementById(this.chartCanvasId).getContext('2d');
            if (this.chart) this.chart.destroy();
            this.chart = new Chart(ctx, {
                type: 'doughnut',
                data: {
                    labels: Object.keys(riskCounts),
                    datasets: [{
                        data: Object.values(riskCounts),
                        backgroundColor: ['#dc2626', '#ea580c', '#ca8a04', '#16a34a']
                    }]
                },
                options: {
                    responsive: true,
                    maintainAspectRatio: false,
                    plugins: { legend: { position: 'right', labels: { font: { size: 10 }, boxWidth: 10 } } }
                }
            });

            const list = document.getElementById(this.listId);
            const highRisk = recs.filter(r => ['CRITICAL', 'HIGH'].includes(r.riskLevel)).slice(0, 10);
            list.innerHTML = highRisk.map(r => `
                <div style="padding: 6px; border-bottom: 1px solid #e5e7eb;">
                    <div><strong>${r.soldierName || '士兵#'+r.soldierId}</strong>
                        <span style="float:right; padding: 1px 6px; border-radius: 3px; font-size: 11px;
                            background: ${r.riskLevel==='CRITICAL'?'#fee2e2':r.riskLevel==='HIGH'?'#ffedd5':'#fef9c3'};
                            color: ${r.riskLevel==='CRITICAL'?'#dc2626':r.riskLevel==='HIGH'?'#ea580c':'#854d0e'};">
                            ${r.riskLevel || '-'}
                        </span>
                    </div>
                    <div style="color: #6b7280;">💊 ${r.recommendedSupplements || '-'}</div>
                    <div style="color: #6b7280;">💰 ¥${(r.estimatedCostTotal||0).toFixed(2)} / ${r.durationDays||14}天</div>
                </div>
            `).join('');

        } catch (e) {
            document.getElementById(this.summaryId).innerHTML = '<p style="color: #ef4444;">干预服务连接失败 (8086未启动)</p>';
        }
    }

    async generateAll() {
        try {
            await fetch(`${this.interventionApi}/v1/intervention-recommendations/generate-all`, { method: 'POST' });
            setTimeout(() => this.load(), 500);
        } catch (e) {
            console.error('生成干预推荐失败:', e);
        }
    }

    bindButton(btnId) {
        const btn = document.getElementById(btnId);
        if (!btn) return;
        this.btnClickHandler = async (e) => {
            btn.disabled = true;
            btn.textContent = '生成中...';
            try {
                await this.generateAll();
            } finally {
                btn.disabled = false;
                btn.textContent = '批量生成推荐';
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
            const btn = document.getElementById('runInterventionBtn');
            if (btn) btn.removeEventListener('click', this.btnClickHandler);
            this.btnClickHandler = null;
        }
    }
}
