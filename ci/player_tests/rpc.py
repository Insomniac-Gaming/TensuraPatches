"""One MC Pilot WebSocket request, executed inside the isolated client container."""

import json
import os
import sys
import uuid

from websockets.sync.client import connect


def request(action: str, params: dict, timeout: float = 45.0):
    port = int(os.environ.get("MCT_CLIENT_WS_PORT", "25560"))
    identifier = str(uuid.uuid4())
    message = {"id": identifier, "action": action, "params": params, "timeoutMs": int(timeout * 1000)}
    with connect(f"ws://127.0.0.1:{port}", open_timeout=5, close_timeout=2,
                 max_size=8 * 1024 * 1024) as socket:
        socket.send(json.dumps(message))
        response = json.loads(socket.recv(timeout=timeout + 2))
    if response.get("id") != identifier:
        raise RuntimeError("MC Pilot returned a response for another request")
    if not response.get("success"):
        raise RuntimeError(f"MC Pilot {action} failed: {response.get('error')}")
    return response.get("data")


if __name__ == "__main__":
    try:
        print(json.dumps(request(sys.argv[1], json.loads(sys.argv[2]),
                                 float(sys.argv[3]) if len(sys.argv) > 3 else 45.0)))
    except Exception as exception:
        print(f"{type(exception).__name__}: {exception}", file=sys.stderr)
        sys.exit(2)
