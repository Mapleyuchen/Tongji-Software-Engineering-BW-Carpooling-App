from flask import Flask

from app.config import Config
from app.extensions import db


def create_app(config_class=Config):
    app = Flask(__name__)
    app.config.from_object(config_class)

    db.init_app(app)

    from app import models  # noqa: F401
    from app.blueprints import register_blueprints

    register_blueprints(app)

    with app.app_context():
        db.create_all()

    return app
