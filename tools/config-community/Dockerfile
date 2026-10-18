FROM python:3.13-slim
WORKDIR /app
COPY server.py .
RUN useradd --uid 10001 --create-home maro && mkdir /data && chown maro:maro /data
USER maro
EXPOSE 8787
CMD ["python", "server.py", "--host", "0.0.0.0", "--db", "/data/community.sqlite"]
