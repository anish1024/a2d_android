"""Security primitives: password hashing and JWT tokens."""
import time
import hashlib
import hmac
import secrets
import base64
import json
from typing import Optional, Tuple

from app.config import get_settings


_settings = get_settings()


def hash_password(password: str) -> str:
    """Hash a password using PBKDF2-HMAC-SHA256 with a salt."""
    salt = secrets.token_bytes(32)
    iterations = 600_000
    dk = hashlib.pbkdf2_hmac("sha256", password.encode(), salt, iterations)
    return f"pbkdf2_sha256${iterations}${base64.b64encode(salt).decode()}${base64.b64encode(dk).decode()}"


def verify_password(password: str, password_hash: str) -> bool:
    """Verify a password against its hash."""
    try:
        scheme, iterations_str, salt_b64, hash_b64 = password_hash.split("$")
        if scheme != "pbkdf2_sha256":
            return False
        iterations = int(iterations_str)
        salt = base64.b64decode(salt_b64)
        expected = base64.b64decode(hash_b64)
        dk = hashlib.pbkdf2_hmac("sha256", password.encode(), salt, iterations)
        return hmac.compare_digest(dk, expected)
    except (ValueError, TypeError):
        return False


def generate_token(user_id: str, device_id: str, ttl_seconds: int, email: Optional[str] = None) -> str:
    """Generate a signed JWT-like token."""
    now = int(time.time())
    payload = {
        "sub": user_id,
        "device_id": device_id,
        "iat": now,
        "exp": now + ttl_seconds,
        "jti": secrets.token_urlsafe(16),
    }
    if email:
        payload["email"] = email
    header = {"alg": "HS256", "typ": "JWT"}
    return _encode_jwt(header, payload, _settings.jwt_secret)


def verify_token(token: str) -> Optional[dict]:
    """Verify a token and return its payload, or None if invalid/expired."""
    try:
        parts = token.split(".")
        if len(parts) != 3:
            return None
        header = json.loads(_b64_decode(parts[0]).decode())
        payload = json.loads(_b64_decode(parts[1]).decode())
        signature = parts[2]
        expected_sig = _sign(f"{parts[0]}.{parts[1]}", _settings.jwt_secret)
        if not hmac.compare_digest(signature, expected_sig):
            return None
        if payload.get("exp", 0) < time.time():
            return None
        return payload
    except (ValueError, KeyError, TypeError):
        return None


def _encode_jwt(header: dict, payload: dict, secret: str) -> str:
    header_seg = _b64_encode(json.dumps(header, separators=(",", ":")).encode())
    payload_seg = _b64_encode(json.dumps(payload, separators=(",", ":")).encode())
    signing_input = f"{header_seg}.{payload_seg}"
    signature = _sign(signing_input, secret)
    return f"{signing_input}.{signature}"


def _sign(data: str, secret: str) -> str:
    sig = hmac.new(secret.encode(), data.encode(), hashlib.sha256).digest()
    return _b64_encode(sig)


def _b64_encode(data: bytes) -> str:
    return base64.urlsafe_b64encode(data).rstrip(b"=").decode()


def _b64_decode(data: str) -> bytes:
    padding = 4 - (len(data) % 4)
    if padding != 4:
        data += "=" * padding
    return base64.urlsafe_b64decode(data)