FROM python:3.11-slim

WORKDIR /app

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY sensor_simulator.py .

CMD python sensor_simulator.py continuous --host emqx --port 1883 --interval ${INTERVAL:-7200} --scurvy ${SCURVY:-0}
