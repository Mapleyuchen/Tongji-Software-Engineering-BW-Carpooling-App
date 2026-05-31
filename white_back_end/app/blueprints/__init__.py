from .auth import auth_bp
from .coupons import coupons_bp
from .order_status import order_status_bp
from .orders import orders_bp
from .payments import payments_bp
from .positions import positions_bp
from .ratings import ratings_bp
from .users import users_bp
from .vehicles import vehicles_bp
from .wallet import wallet_bp


def register_blueprints(app):
    app.register_blueprint(auth_bp)
    app.register_blueprint(users_bp)
    app.register_blueprint(orders_bp)
    app.register_blueprint(positions_bp)
    app.register_blueprint(order_status_bp)
    app.register_blueprint(ratings_bp)
    app.register_blueprint(vehicles_bp)
    app.register_blueprint(coupons_bp)
    app.register_blueprint(payments_bp)
    app.register_blueprint(wallet_bp)
