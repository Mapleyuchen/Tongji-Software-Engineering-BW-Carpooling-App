import datetime

import jwt
from flask import Blueprint, jsonify, request

from app.extensions import db
from app.models import (
    Coupon,
    DriverAverageRating,
    DriverRating,
    Order,
    OrderStatus,
    User,
    UserCoupon,
    Vehicle,
)
from app.utils.auth import check_token, generate_token


positions_bp = Blueprint("positions", __name__)


posData = {
    "嘉定校区": (121.214728, 31.285629),
    "四平路校区": (121.501689, 31.28325),
    "虹桥火车站": (121.320674, 31.194062),
    "上海南站": (121.429494, 31.153132),
    "上海浦东国际机场": (121.805599, 31.150975),
    "上海站": (121.455719, 31.249558),
    "上海西站": (121.402403, 31.262795),
    "上海松江站": (121.228837, 30.985087)
}

@positions_bp.route('/api/getpos', methods=['GET'])
def get_pos():
    # 将元组解包为 JSON 所需结构
    data_list = [{"name": name, "lon": lon, "lat": lat} for name, (lon, lat) in posData.items()]

    return jsonify({
        "code": 200,
        "message": "获取成功",
        "data": {
            "table": data_list
        }
    })

