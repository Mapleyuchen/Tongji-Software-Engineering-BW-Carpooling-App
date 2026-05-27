from flask import Flask

from app.config import Config
from app.extensions import db, socketio


def create_app(config_class=Config):
    app = Flask(__name__)
    app.config.from_object(config_class)

    db.init_app(app)
    socketio_options = {
        "async_mode": app.config["SOCKETIO_ASYNC_MODE"],
        "cors_allowed_origins": app.config["SOCKETIO_CORS_ALLOWED_ORIGINS"],
    }
    if app.config["SOCKETIO_REDIS_URL"]:
        socketio_options["message_queue"] = app.config["SOCKETIO_REDIS_URL"]
    socketio.init_app(app, **socketio_options)

    from app import models  # noqa: F401
    from app.sockets import chat  # noqa: F401
    from app.blueprints import register_blueprints
    
    register_blueprints(app)
    
    with app.app_context():
        db.create_all()
    
    return app
