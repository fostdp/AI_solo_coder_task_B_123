class EpidemicCurve {
    constructor(apiBase, seirSimulatorApi, chartCanvasId, summaryId, comparisonId, barracksSelectId, initialInfectedInputId) {
        this.apiBase = apiBase;
        this.seirSimulatorApi = seirSimulatorApi;
        this.chartCanvasId = chartCanvasId;
        this.summaryId = summaryId;
        this.comparisonId = comparisonId;
        this.barracksSelectId = barracksSelectId;
        this.initialInfectedInputId = initialInfectedInputId;
        this.chart = null;
        this.btnClickHandler = null;
    }

    async loadBarracksOptions() {
        try {
            const res = await fetch(`${this.apiBase}/v1/barracks`);
            const data = await res.json();
            const select = document.getElementById(this.barracksSelectId);
            if (select) {
                select.innerHTML = data.map(b => `<option value="${b.id}">${b.name}</option>`).join('');
            }
        } catch (e) {}
    }

    async runSimulation(barracksId, initialInfected, simulationDays, quarantineStartDay, isolationEffectiveness) {
        try {
            const res = await fetch(`${this.seirSimulatorApi}/v1/seir-simulations/run`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    barracksId: parseInt(barracksId),
                    initialInfected: initialInfected,
                    simulationDays: simulationDays,
                    quarantineStartDay: quarantineStartDay,
                    isolationEffectiveness: isolationEffectiveness
                })
            });
            const sim = await res.json();
            this._renderResult(sim);
        } catch (e) {
            console.error('SEIR模拟失败:', e);
        }
    }

    _renderResult(sim) {
        const summary = document.getElementById(this.summaryId);
        const labels = [];
        const sData = [], eData = [], iData = [], rData = [], qData = [];

        if (sim && sim.timePoints) {
            sim.timePoints.forEach(tp => {
                labels.push('D' + tp.day);
                sData.push(tp.susceptibleCount || 0);
                eData.push(tp.exposedCount || 0);
                iData.push(tp.infectedCount || 0);
                rData.push(tp.recoveredCount || 0);
                qData.push(tp.quarantinedCount || 0);
            });
            summary.innerHTML = `
                <div><strong>R₀基本再生数:</strong> ${(sim.transmissionRateBeta / sim.recoveryRateGamma).toFixed(2)}</div>
                <div><strong>峰值日:</strong> 第${sim.peakDay || '-'}天</div>
                <div><strong>峰值感染:</strong> ${sim.maxInfectedCount || 0}人</div>
                <div><strong>总感染人数:</strong> ${sim.totalInfectedCount || 0}人</div>
            `;
        }

        const ctx = document.getElementById(this.chartCanvasId).getContext('2d');
        if (this.chart) this.chart.destroy();
        this.chart = new Chart(ctx, {
            type: 'line',
            data: {
                labels,
                datasets: [
                    { label: '易感(S)', data: sData, borderColor: '#60a5fa', backgroundColor: 'rgba(96,165,250,0.1)', fill: true, tension: 0.3 },
                    { label: '潜伏(E)', data: eData, borderColor: '#fbbf24', backgroundColor: 'rgba(251,191,36,0.1)', fill: true, tension: 0.3 },
                    { label: '感染(I)', data: iData, borderColor: '#ef4444', backgroundColor: 'rgba(239,68,68,0.1)', fill: true, tension: 0.3 },
                    { label: '恢复(R)', data: rData, borderColor: '#22c55e', backgroundColor: 'rgba(34,197,94,0.1)', fill: true, tension: 0.3 },
                    { label: '隔离(Q)', data: qData, borderColor: '#a855f7', backgroundColor: 'rgba(168,85,247,0.1)', fill: true, tension: 0.3, borderDash: [5,5] }
                ]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                plugins: { legend: { position: 'bottom', labels: { font: { size: 10 }, boxWidth: 10 } } },
                scales: { y: { beginAtZero: true } }
            }
        });
    }

    bindButton(btnId) {
        const btn = document.getElementById(btnId);
        if (!btn) return;
        this.btnClickHandler = async (e) => {
            btn.disabled = true;
            btn.textContent = '模拟中...';
            try {
                const barracksId = document.getElementById(this.barracksSelectId).value;
                const initInfected = parseInt(document.getElementById(this.initialInfectedInputId).value) || 2;
                await this.runSimulation(barracksId, initInfected, 60, 10, 0.6);
            } finally {
                btn.disabled = false;
                btn.textContent = '运行模拟';
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
            const btn = document.getElementById('runSeirBtn');
            if (btn) btn.removeEventListener('click', this.btnClickHandler);
            this.btnClickHandler = null;
        }
    }
}
