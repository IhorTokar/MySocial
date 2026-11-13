"""Мінімальний клієнт REST API MySocial (лише стандартна бібліотека Python)."""

import json
import urllib.error
import urllib.parse
import urllib.request


class ApiError(Exception):
    def __init__(self, status, message):
        super().__init__(f"HTTP {status}: {message}")
        self.status = status
        self.message = message


class Api:
    def __init__(self, base_url="http://localhost:8080", token=None, timeout=30):
        self.base_url = base_url.rstrip("/")
        self.token = token
        self.timeout = timeout

    # ----- запити -----

    def request(self, method, path, body=None, params=None, timeout=None):
        url = self.base_url + path
        if params:
            url += "?" + urllib.parse.urlencode(params)
        data = None
        headers = {"Accept": "application/json"}
        if body is not None:
            data = json.dumps(body).encode("utf-8")
            headers["Content-Type"] = "application/json; charset=utf-8"
        if self.token:
            headers["Authorization"] = "Bearer " + self.token
        req = urllib.request.Request(url, data=data, method=method, headers=headers)
        try:
            with urllib.request.urlopen(req, timeout=timeout or self.timeout) as resp:
                raw = resp.read().decode("utf-8")
                return json.loads(raw) if raw.strip() else None
        except urllib.error.HTTPError as e:
            raw = e.read().decode("utf-8", errors="replace")
            try:
                message = json.loads(raw).get("error", raw)
            except (ValueError, AttributeError):
                message = raw
            raise ApiError(e.code, message) from None

    def get(self, path, **params):
        return self.request("GET", path, params=params or None)

    def post(self, path, body=None, timeout=None, **params):
        return self.request("POST", path, body=body, params=params or None, timeout=timeout)

    def delete(self, path, body=None, **params):
        return self.request("DELETE", path, body=body, params=params or None)

    # ----- автентифікація -----

    def login(self, username, password):
        """Повертає новий Api з токеном цього користувача."""
        resp = self.post("/api/auth/login", {"username": username, "password": password})
        api = Api(self.base_url, resp["token"], self.timeout)
        api.user_id = resp["userId"]
        api.username = resp["username"]
        api.role = resp["role"]
        return api

    def register(self, username, email, password):
        return self.post("/api/auth/register", {"username": username, "email": email, "password": password})