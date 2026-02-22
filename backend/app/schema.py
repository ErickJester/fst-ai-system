from .db import engine, Base
from . import models  # noqa: F401

def init_db():
    Base.metadata.create_all(bind=engine)
