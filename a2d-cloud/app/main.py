from contextlib import asynccontextmanager
from pathlib import Path

from fastapi import FastAPI
from fastapi.staticfiles import StaticFiles
from fastapi.responses import FileResponse

from app.config import get_settings
from app.control_db import init_control_db
from app.tenant import init_tenant_store
from app.api.auth import router as auth_router


settings = get_settings()


@asynccontextmanager
async def lifespan(app: FastAPI):
    # Startup: ensure control DB tables exist and tenant store is ready.
    init_control_db()
    db_path = settings.database_url.replace("sqlite:///", "")
    init_tenant_store(str(Path(db_path).parent))
    yield


app = FastAPI(title=settings.app_name, lifespan=lifespan)

# Static assets (login page CSS/JS)
app.mount("/static", StaticFiles(directory="app/web/static"), name="static")

# API routes
app.include_router(auth_router)


@app.get("/healthz")
async def healthz():
    return {"status": "ok"}


@app.get("/readyz")
async def readyz():
    return {"status": "ready"}


@app.get("/login")
async def login_page():
    return FileResponse("app/web/templates/login.html")


@app.get("/")
async def root():
    return {"service": "a2d-cloud", "version": "0.1.0", "login": "/login"}