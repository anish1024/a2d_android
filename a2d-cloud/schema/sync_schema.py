"""Sync API schema definitions."""
from pydantic import BaseModel, Field, validator
from typing import Optional, List, Dict, Any
from datetime import datetime
import base64
import json

class SyncRequest(BaseModel):
    """Request body for sync backup."""
    idempotency_key: Optional[str] = Field(
        default=None,
        description="Idempotency key for retry safety",
        max_length=36,
        pattern=r'^[a-f0-9]{32}$'
    )
    
    # Table-specific data structure - we'll handle this dynamically
    # This is a generic container for table rows
    data: Dict[str, Any] = Field(
        default_factory=dict,
        description="Table name as key mapping to row data"
    )
    
    # Optional: client timestamp for download verification
    lastBackupAt: Optional[int] = Field(
        default=None,
        description="Client timestamp in milliseconds since epoch"
    )
    
    class Config:
        # Allow arbitrary types in data field
        arbitrary_types_allowed = True

class SyncResponse(BaseModel):
    """Response for sync backup."""
    accepted: List[Dict[str, Any]] = Field(
        description="Rows that were successfully merged"
    )
    rejected: List[Dict[str, Any]] = Field(
        description="Rows that failed validation/merge"
    )
    nextDownloadToken: Optional[str] = Field(
        default=None,
        description="Token for next download request (if more data available)"
    )
    nextDownloadAt: Optional[int] = Field(
        default=None,
        description="Server timestamp to use for next download (ms since epoch)"
    )
    nextBatchCount: int = Field(
        default=0,
        description="Number of rows in next batch (for pagination)"
    )

class SyncUpdateRequest(BaseModel):
    """Request for sync updates."""
    lastDownloadAt: str = Field(
        description="Opaque token from previous download response"
    )
    
    # Optional: limit for batch size (1-500)
    limit: int = Field(
        default=500,
        ge=1,
        le=500,
        description="Maximum number of rows to return per page"
    )
    
    class Config:
        arbitrary_types_allowed = True

class SyncUpdateResponse(BaseModel):
    """Response for sync updates."""
    rows: List[Dict[str, Any]] = Field(
        description="Rows changed since last download"
    )
    nextDownloadToken: Optional[str] = Field(
        default=None,
        description="Opaque token for next download request"
    )
    nextDownloadAt: Optional[int] = Field(
        default=None,
        description="Server timestamp for next download (ms since epoch)"
    )
    nextBatchCount: int = Field(
        default=0,
        description="Number of rows in next batch"
    )