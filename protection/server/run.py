import json, os
from pathlib import Path
import uvicorn
from service import Settings, create_app

config=json.loads(Path(os.environ['MARO_LICENSE_CONFIG']).read_text())
app=create_app(Settings(**config))
if __name__=='__main__':
    # Single-host SQLite transactions are shared between workers. Bind loopback behind a TLS proxy.
    # Access logs are disabled so request details cannot accidentally become credential telemetry.
    uvicorn.run(app,host='127.0.0.1',port=8788,proxy_headers=False,access_log=False,log_level='warning',limit_concurrency=32)
