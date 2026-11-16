from functools import lru_cache
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        extra="ignore",
    )

    # Application
    app_name: str = "A2D Cloud"
    app_host: str = "127.0.0.1"
    app_port: int = 8000
    debug: bool = False

    # Database
    database_url: str = "sqlite:///./data/control.db"

    # Auth
    jwt_secret: str = "change-me-generate-with-openssl-rand-hex-32"
    access_token_ttl_seconds: int = 900
    refresh_token_ttl_seconds: int = 2592000

    # Public URL (Cloudflare Tunnel)
    public_base_url: str = "https://a2d.example.com"

    # Limits
    max_import_bytes: int = 10_485_760


@lru_cache
def get_settings() -> Settings:
    return Settings()