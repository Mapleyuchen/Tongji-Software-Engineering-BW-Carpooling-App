import datetime

from app.extensions import db


class User(db.Model):
    __tablename__ = "user"
    username = db.Column(db.String(20), nullable=False, unique=True, primary_key=True)
    password = db.Column(db.String(200), nullable=False)
    phonenumber = db.Column(db.String(11), nullable=False)
    usertype = db.Column(db.Integer, nullable=False)
    # usertype 区分用户类型 1为一般用户 2为司机

