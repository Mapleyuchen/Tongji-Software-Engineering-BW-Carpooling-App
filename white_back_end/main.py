from flask import Flask, request, jsonify, make_response
from flask_sqlalchemy import SQLAlchemy
from flask_cors import CORS
from werkzeug.security import generate_password_hash, check_password_hash
import datetime
import jwt
import logging
import uuid

# 支付宝沙箱集成：配置/客户端工厂在独立模块，私钥不落盘到代码
from alipay_config import (
    AlipayConfigError,
    get_alipay_client,
    get_gateway,
    get_notify_url,
    get_return_url,
)

# 用于在 /api/payment/notify 中验签
_log = logging.getLogger("alipay")

app = Flask(__name__)
app.secret_key = 'xxxxxx'

# 配置CORS
# CORS(app, resources={r"/api/*": {"origins": "http://localhost:5173"}})
# CORS(app, resources={r"/api/*": {"origins": "*"}})

# 导通mysql并建立映射
host = "localhost",
port = 3306,
user = "root",
password = "Fq741026_",
database = "white_web",
charset = "utf8"
# 配置app参数
app.config['SQLALCHEMY_DATABASE_URI'] = 'mysql+pymysql://root:Fq741026_@localhost:3306/white_web?charset=utf8mb4'
app.config['SQLALCHEMY_TRACK_MODIFICATIONS'] = True  # True跟踪数据库的修改，及时发送信号
db = SQLAlchemy(app)

with app.app_context():
    db.create_all()


class User(db.Model):
    __tablename__ = "user"
    username = db.Column(db.String(20), nullable=False, unique=True, primary_key=True)
    password = db.Column(db.String(200), nullable=False)
    phonenumber = db.Column(db.String(11), nullable=False)
    usertype = db.Column(db.Integer, nullable=False)
    # usertype 区分用户类型 1为一般用户 2为司机


class Vehicle(db.Model):
    __tablename__ = "vehicle"
    vehicle_id = db.Column(db.Integer, primary_key=True, autoincrement=True)
    driver_username = db.Column(db.String(20), db.ForeignKey("user.username"), nullable=False)
    license_plate = db.Column(db.String(20), nullable=False, unique=True)  # 车牌号
    brand = db.Column(db.String(50), nullable=False)  # 品牌
    model = db.Column(db.String(50), nullable=False)  # 型号
    color = db.Column(db.String(20), nullable=False)  # 颜色
    seat_count = db.Column(db.Integer, nullable=False, default=4)  # 座位数
    is_verified = db.Column(db.Boolean, default=False)  # 是否认证
    created_at = db.Column(db.DateTime, default=datetime.datetime.now)


class Coupon(db.Model):
    __tablename__ = "coupon"
    coupon_id = db.Column(db.Integer, primary_key=True, autoincrement=True)
    coupon_name = db.Column(db.String(100), nullable=False)
    discount_type = db.Column(db.String(20), nullable=False)  # percentage, fixed, ……
    discount_value = db.Column(db.Numeric(10, 2), nullable=False)
    min_amount = db.Column(db.Numeric(10, 2), default=0)  # 最低消费金额
    start_date = db.Column(db.Date, nullable=False)
    end_date = db.Column(db.Date, nullable=False)
    usage_limit = db.Column(db.Integer, default=1)  # 使用次数限制
    is_active = db.Column(db.Boolean, default=True)
    created_at = db.Column(db.DateTime, default=datetime.datetime.now)


class UserCoupon(db.Model):
    __tablename__ = "user_coupon"
    id = db.Column(db.Integer, primary_key=True, autoincrement=True)
    username = db.Column(db.String(20), db.ForeignKey("user.username"))
    coupon_id = db.Column(db.Integer, db.ForeignKey("coupon.coupon_id"))
    used_count = db.Column(db.Integer, default=0)
    obtained_at = db.Column(db.DateTime, default=datetime.datetime.now)


# 用户登录
@app.route('/api/login', methods=['POST'])
def login():
    data = request.json
    username = data.get('username')
    password = data.get('password')

    # 验证用户
    user = User.query.filter_by(username=username).first()
    if user and (check_password_hash(user.password, password)):
        # 登录成功
        token = generate_token(user.username)  # 生成Token
        print('登录成功')
        return jsonify({
            "code": 200,
            "message": "登录成功",
            "data": {
                "token": token,
                "username": user.username,
                "usertype": user.usertype
            }
        })
    else:
        # 登录失败
        print('登录失败')
        return jsonify({
            "code": 401,
            "message": "账号或密码错误"
        })


# 用户注册
@app.route('/api/register', methods=['POST'])
def register():
    data = request.json
    username = data.get('username')
    password = data.get('password')
    phonenumber = data.get('phonenumber')
    usertype = data.get('usertype')

    # 验证用户
    user = User.query.filter_by(username=username).first()
    if user:
        # 用户已存在
        return jsonify({
            "code": 401,
            "message": "用户已存在"
        })
    # 添加用户
    user = User(username=username, password=generate_password_hash(password), phonenumber=phonenumber,
                usertype=usertype)
    db.session.add(user)
    db.session.commit()
    print('注册成功')
    token = generate_token(user.username)  # 生成Token
    return jsonify({
        "code": 200,
        "message": "注册成功",
        "data": {
            "token": token,
            "username": user.username
        }
    })


# 查看表格
@app.route('/api/look', methods=['GET'])
def look():
    # 获取请求头中的Token
    token = request.headers.get('Authorization')
    if not token:
        return jsonify({"code": 401, "message": "缺少Token"}), 401

    message = check_token(token)
    if message:
        return message

    # 查询所有用户数据
    users = User.query.all()
    table_data = [
        {
            "username": user.username,
            "password": user.password
        } for user in users
    ]
    return jsonify({
        "code": 200,
        "message": "查询成功",
        "data": {
            "table": table_data
        }
    })


# 生成Token
def generate_token(username):
    payload = {
        "username": username,
        "exp": datetime.datetime.now() + datetime.timedelta(hours=1)  # 1小时有效期
    }
    token = jwt.encode(payload, "secret_key", algorithm="HS256")
    return token


def check_token(token):
    print(token)
    # 解码Token
    try:
        payload = jwt.decode(token, "secret_key", algorithms=["HS256"])
        username = payload['username']
        user = User.query.filter_by(username=username).first()
        if not user:
            return jsonify({"code": 401, "message": "用户不存在"})
    except jwt.ExpiredSignatureError:
        return jsonify({"code": 401, "message": "Token已过期"})
    except jwt.InvalidTokenError:
        return jsonify({"code": 401, "message": "无效的Token"})
    return None


class Order(db.Model):
    __tablename__ = 'orders'
    order_id = db.Column(db.Integer, primary_key=True, unique=True, nullable=False)  # 订单编号
    user1 = db.Column(db.String(20), nullable=True)  # 拼车的四位用户
    user2 = db.Column(db.String(20), nullable=True)
    user3 = db.Column(db.String(20), nullable=True)
    user4 = db.Column(db.String(20), nullable=True)
    driver = db.Column(db.String(20), nullable=True)
    departure = db.Column(db.String(100), nullable=False)  # 出发地
    destination = db.Column(db.String(100), nullable=False)  # 目的地
    date = db.Column(db.Date, nullable=False)  # 日期
    earliest_departure_time = db.Column(db.Time, nullable=False)  # 最早发车时间
    latest_departure_time = db.Column(db.Time, nullable=False)  # 最晚发车时间
    remark = db.Column(db.String(100), nullable=False)  # remark

    def __repr__(self):
        return f'<Order {self.order_id}>'


# 获取所有订单
@app.route('/api/orders', methods=['GET'])
def get_orders():
    orders = Order.query.all()
    data = [
        {
            "order_id": order.order_id,
            "user1": order.user1,
            "user2": order.user2,
            "user3": order.user3,
            "user4": order.user4,
            "driver": order.driver,
            "departure": order.departure,
            "destination": order.destination,
            "date": order.date.isoformat(),
            "earliest_departure_time": order.earliest_departure_time.isoformat(),
            "latest_departure_time": order.latest_departure_time.isoformat(),
            "remark": order.remark
        } for order in orders
    ]
    return jsonify({
        "code": 200,
        "message": "查询成功",
        "data": {
            "list": data
        }
    })


# 添加新订单
@app.route('/api/orders/add', methods=['POST'])
def add_order():
    # 获取请求头中的Token
    token = request.headers.get('Authorization')
    if not token:
        return jsonify({
            "code": 401,
            "message": "Token缺失"
        }), 401

    # 检查Token的有效性
    check_result = check_token(token)
    if check_result:
        return check_result  # 如果Token无效，直接返回错误信息

    payload = jwt.decode(token, "secret_key", algorithms=["HS256"])
    current_user = payload['username']  # 解析订单发起人
    user_info = User.query.get_or_404(current_user)

    if user_info.usertype == 2:
        return jsonify({
            "code": 403,
            "message": "您当前身份为司机，不可新建订单"
        }), 403

    data = request.json
    new_order = Order(
        user1=current_user,
        departure=data['departure'],
        destination=data['destination'],
        date=datetime.datetime.strptime(data['date'], '%Y-%m-%d').date(),
        earliest_departure_time=datetime.datetime.strptime(data['earliest_departure_time'], '%H:%M').time(),
        latest_departure_time=datetime.datetime.strptime(data['latest_departure_time'], '%H:%M').time(),
        remark=data['remark']
    )
    db.session.add(new_order)
    db.session.commit()
    data = {
            "order_id": new_order.order_id,
            "departure": new_order.departure,
            "destination": new_order.destination,
            "date": new_order.date.isoformat(),
            "earliest_departure_time": new_order.earliest_departure_time.isoformat(),
            "latest_departure_time": new_order.latest_departure_time.isoformat(),
            "remark": new_order.remark
        }
    return jsonify({
        "code": 201,
        "message": "Order added successfully",
        "username": current_user,
        "data": data
    }), 201


# 获取单个订单
@app.route('/api/orders/<int:order_id>', methods=['GET'])
def get_order(order_id):
    order = Order.query.get_or_404(order_id)
    return jsonify({
        "code": 200,
        "message": "查询成功",
        "data":
            {
                "order_id": order.order_id,
                "user1": order.user1,
                "user2": order.user2,
                "user3": order.user3,
                "user4": order.user4,
                "driver": order.driver,
                "departure": order.departure,
                "destination": order.destination,
                "date": order.date.isoformat(),
                "earliest_departure_time": order.earliest_departure_time.isoformat(),
                "latest_departure_time": order.latest_departure_time.isoformat(),
                "remark": order.remark
            }
    })


# 删除订单
@app.route('/api/orders/leave', methods=['POST'])
def delete_order():
    # 获取请求头中的Token
    token = request.headers.get('Authorization')
    if not token:
        return jsonify({
            "code": 401,
            "message": "Token缺失"
        }), 401

    # 检查Token的有效性
    check_result = check_token(token)
    if check_result:
        return check_result  # 如果Token无效，直接返回错误信息

    # 获取请求体中的order_id
    data = request.json
    order_id = data.get('order_id')
    if not order_id:
        return jsonify({
            "code": 400,
            "message": "order_id缺失"
        }), 400

    payload = jwt.decode(token, "secret_key", algorithms=["HS256"])
    current_user = payload['username']

    order = Order.query.get_or_404(order_id)
    user_info = User.query.get_or_404(current_user)
    if user_info.usertype == 1:
        users = [order.user1, order.user2, order.user3, order.user4]

        non_empty_users = [user for user in users if user is not None]
        non_empty_count = len(non_empty_users)

        # 移除当前用户
        if current_user in non_empty_users:
            if order.user1 == current_user:
                order.user1 = None
            elif order.user2 == current_user:
                order.user2 = None
            elif order.user3 == current_user:
                order.user3 = None
            elif order.user4 == current_user:
                order.user4 = None

            # 重新排列用户
            users = [order.user1, order.user2, order.user3, order.user4]
            users = [user for user in users if user is not None]
            order.user1, order.user2, order.user3, order.user4 = (
                users[0] if len(users) > 0 else None,
                users[1] if len(users) > 1 else None,
                users[2] if len(users) > 2 else None,
                users[3] if len(users) > 3 else None
            )

            db.session.commit()

            # 检查是否所有用户都离开了
            if len(users) == 0:
                # 先删除与该订单关联的所有评分记录
                DriverRating.query.filter_by(order_id=order.order_id).delete()
                
                # 删除订单状态记录
                OrderStatus.query.filter_by(order_id=order.order_id).delete()
                
                # 然后删除订单本身
                db.session.delete(order)
                db.session.commit()

            return jsonify({
                "code": 200,
                "message": "离开订单成功"
            })
        else:
            return jsonify({
                "code": 404,
                "message": "订单中没有该用户"
            }), 404

    elif user_info.usertype == 2:
        if order.driver == current_user:
            order.driver = None
            db.session.commit()
            return jsonify({
                "code": 200,
                "message": "离开订单成功"
            })
        else:
            return jsonify({
                "code": 404,
                "message": "订单中没有该用户"
            }), 404
    else:
        return jsonify({
            "code": 404,
            "message": "用户类型错误"
        }), 404


@app.route('/api/orders/join', methods=['POST'])
def join_order():
    # 获取请求头中的Token
    token = request.headers.get('Authorization')
    if not token:
        return jsonify({
            "code": 401,
            "message": "Token缺失"
        }), 401

    # 检查Token的有效性
    check_result = check_token(token)
    if check_result:
        return check_result  # 如果Token无效，直接返回错误信息

    payload = jwt.decode(token, "secret_key", algorithms=["HS256"])
    current_user = payload['username']  # 解析加入订单的用户

    data = request.json
    order_id = data.get('order_id')

    if not order_id:
        return jsonify({
            "code": 400,
            "message": "order_id缺失"
        }), 400

    order = Order.query.get_or_404(order_id)
    # 检查订单是否已满员
    user_info = User.query.get_or_404(current_user)
    if user_info.usertype == 1:  # 一般用户
        users = [order.user1, order.user2, order.user3, order.user4]
        non_empty_users = [user for user in users if user is not None]
        if len(non_empty_users) >= 4:
            return jsonify({
                "code": 422,
                "message": "加入失败，订单已满员"
            }), 422
        # 订单没有满员
        if order.user1 is None:
            order.user1 = current_user
        elif order.user2 is None:
            order.user2 = current_user
        elif order.user3 is None:
            order.user3 = current_user
        elif order.user4 is None:
            order.user4 = current_user

        db.session.commit()

        return jsonify({
            "code": 200,
            "message": "加入订单成功"
        }), 200

    elif user_info.usertype == 2:  # 司机
        if order.driver is None:
            order.driver = current_user
            db.session.commit()
            return jsonify({
                "code": 200,
                "message": "加入订单成功"
            }), 200

        else:
            return jsonify({
                "code": 422,
                "message": "加入失败，订单内已有司机"
            }), 422
    else:
        return jsonify({
            "code": 404,
            "message": "用户类型错误"
        }), 404

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

@app.route('/api/getpos', methods=['GET'])
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


@app.route('/api/orders/search/<string:keyword>', methods=['GET'])
def search_orders(keyword):
    search_condition = f"%{keyword}%"
    # 去和字段departure以及destination做匹配
    orders = Order.query.filter(
        (Order.departure.ilike(search_condition)) | (Order.destination.ilike(search_condition))
    ).order_by(Order.date.asc()).all()

    result = [
        {
            "order_id": order.order_id,
            "user1": order.user1,
            "user2": order.user2,
            "user3": order.user3,
            "user4": order.user4,
            "driver": order.driver,
            "departure": order.departure,
            "destination": order.destination,
            "date": order.date.isoformat(),
            "earliest_departure_time": order.earliest_departure_time.isoformat(),
            "latest_departure_time": order.latest_departure_time.isoformat(),
            "remark": order.remark
        } for order in orders
    ]
        
    return jsonify({
        "code": 200,
        "message": "搜索成功",
        "data": {
            "list": result
        }
    })


@app.route('/api/user/<string:username>', methods=['GET'])
def user_info(username):
    user = User.query.get_or_404(username)
    if user.usertype == 1:
        type = '一般用户'
    elif user.usertype == 2:
        type = '司机'
    else:
        type = '类型错误'
    return jsonify({
        "code": 200,
        "message": "查询成功",
        "data":
            {
                "phonenumber": user.phonenumber,
                "usertype": f"{type}"
            }
    })


@app.route('/api/user/orders', methods=['GET'])
def user_orders():
    # 获取请求头中的Token
    token = request.headers.get('Authorization')
    if not token:
        return jsonify({
            "code": 401,
            "message": "Token缺失"
        }), 401

    # 检查Token的有效性
    check_result = check_token(token)
    if check_result:
        return check_result  # 如果Token无效，直接返回错误信息

    payload = jwt.decode(token, "secret_key", algorithms=["HS256"])
    current_user = payload['username']  # 当前用户

    # 查询当前用户参与的所有订单
    orders = Order.query.filter(
        (Order.user1 == current_user) |
        (Order.user2 == current_user) |
        (Order.user3 == current_user) |
        (Order.user4 == current_user)
    ).all()

    result = []
    for order in orders:
        result.append({
            "order_id": order.order_id,
            "user1": order.user1,
            "user2": order.user2,
            "user3": order.user3,
            "user4": order.user4,
            "driver": order.driver,
            "departure": order.departure,
            "destination": order.destination,
            "date": order.date.isoformat(),
            "earliest_departure_time": order.earliest_departure_time.isoformat(),
            "latest_departure_time": order.latest_departure_time.isoformat(),
            "remark": order.remark
        })

    return jsonify({
        "code": 200,
        "message": "查询成功",
        "data": {
            "list": result
        }
    })


class OrderStatus(db.Model):
    __tablename__ = "order_status"
    order_id = db.Column(db.Integer, db.ForeignKey("orders.order_id"), primary_key=True)
    status = db.Column(db.Integer, default=0)  # 0: 未开始, 1: 进行中, 2: 已完成
    user1_arrived = db.Column(db.Boolean, default=False)
    user2_arrived = db.Column(db.Boolean, default=False)
    user3_arrived = db.Column(db.Boolean, default=False)
    user4_arrived = db.Column(db.Boolean, default=False)
    driver_arrived = db.Column(db.Boolean, default=False)
    completed_at = db.Column(db.DateTime, nullable=True)  # 订单完成时间


class DriverRating(db.Model):
    __tablename__ = "driver_rating"
    rating_id = db.Column(db.Integer, primary_key=True, autoincrement=True)
    order_id = db.Column(db.Integer, db.ForeignKey("orders.order_id"))
    driver_username = db.Column(db.String(20), db.ForeignKey("user.username"))
    user_username = db.Column(db.String(20), db.ForeignKey("user.username"))
    rating = db.Column(db.Float, nullable=False)  # 1-5分
    created_at = db.Column(db.DateTime, default=datetime.datetime.now)


# 司机平均评分表 - 不修改User表，而是创建新表存储评分信息
class DriverAverageRating(db.Model):
    __tablename__ = "driver_average_rating"
    driver_username = db.Column(db.String(20), db.ForeignKey("user.username"), primary_key=True)
    average_rating = db.Column(db.Float, default=5.0)  # 默认5分
    rating_count = db.Column(db.Integer, default=0)  # 评分次数


# 获取用户最近的订单
@app.route("/api/user/current-order", methods=["GET"])
def get_current_order():
    # 获取请求头中的Token
    token = request.headers.get("Authorization")
    if not token:
        return jsonify({"code": 401, "message": "Token缺失"}), 401

    # 检查Token的有效性
    check_result = check_token(token)
    if check_result:
        return check_result  # 如果Token无效，直接返回错误信息

    payload = jwt.decode(token, "secret_key", algorithms=["HS256"])
    current_user = payload["username"]  # 当前用户

    # 获取当前日期和时间
    current_date = datetime.datetime.now().date()
    current_time = datetime.datetime.now()

    # 查询当天用户参与的所有订单
    today_orders = Order.query.filter(((Order.user1 == current_user) | (Order.user2 == current_user) | (Order.user3 == current_user) | (Order.user4 == current_user) | (Order.driver == current_user)) & (Order.date == current_date)).all()

    # 没有当天订单
    if not today_orders:
        return jsonify({"code": 404, "message": "未找到相关订单"})

    # 将订单分为三类：已完成、进行中、未开始
    completed_orders = []  # 已完成订单 (status = 2)
    in_progress_orders = []  # 进行中订单 (status = 1)
    not_started_orders = []  # 未开始订单 (status = 0)

    for order in today_orders:
        order_status = OrderStatus.query.filter_by(order_id=order.order_id).first()

        # 如果没有状态记录，创建一个
        if not order_status:
            order_status = OrderStatus(order_id=order.order_id, status=0, user1_arrived=False, user2_arrived=False, user3_arrived=False, user4_arrived=False, driver_arrived=False)
            db.session.add(order_status)
            db.session.commit()

        if order_status.status == 2:  # 已完成
            completed_orders.append((order, order_status))
        elif order_status.status == 1:  # 进行中
            in_progress_orders.append((order, order_status))
        elif order_status.status == 0:  # 未开始
            not_started_orders.append((order, order_status))

    # 对各类订单按时间排序
    completed_orders.sort(key=lambda x: x[1].completed_at if x[1].completed_at else datetime.datetime.min, reverse=True)
    in_progress_orders.sort(key=lambda x: datetime.datetime.combine(x[0].date, x[0].earliest_departure_time))
    not_started_orders.sort(key=lambda x: datetime.datetime.combine(x[0].date, x[0].earliest_departure_time))

    target_order = None
    target_order_status = None

    # 优先级1：显示进行中订单（取最早的）
    if in_progress_orders:
        target_order, target_order_status = in_progress_orders[0]

    # 优先级2：显示已超过开始时间或开始时间在30分钟内的未开始订单
    elif not_started_orders:
        # 筛选出已超过开始时间的未开始订单
        overdue_orders = []
        future_orders = []

        for order, status in not_started_orders:
            order_start_time = datetime.datetime.combine(order.date, order.earliest_departure_time)
            time_diff = (order_start_time - current_time).total_seconds() / 60  # 转换为分钟

            if order_start_time <= current_time:  # 已超过开始时间
                overdue_orders.append((order, status))
            else:
                future_orders.append((order, status, time_diff))

        # 按时间差排序
        future_orders.sort(key=lambda x: x[2])  # 按时间差升序排序

        if overdue_orders:  # 有已超过开始时间的未开始订单
            target_order, target_order_status = overdue_orders[0]  # 取最早的
        elif future_orders and completed_orders:
            # 判断最近的未开始订单是否在30分钟内
            nearest_future_order, nearest_future_status, time_diff = future_orders[0]

            # 检查用户是否已对最近完成的订单评分
            last_completed_order, last_completed_status = completed_orders[0]
            has_rated = True  # 默认假设已评分

            if last_completed_order.driver:
                # 查询用户是否已经评分
                existing_rating = DriverRating.query.filter_by(order_id=last_completed_order.order_id, user_username=current_user, driver_username=last_completed_order.driver).first()
                has_rated = existing_rating is not None

                # 如果是司机，视为已评分（司机不需要评价自己）
                if current_user == last_completed_order.driver:
                    has_rated = True

            # 如果最近的未开始订单开始时间在30分钟内，显示未开始订单
            # 否则，如果用户未评分，显示已完成订单
            if time_diff <= 30:
                target_order, target_order_status = nearest_future_order, nearest_future_status
            elif not has_rated and current_user != last_completed_order.driver:
                target_order, target_order_status = last_completed_order, last_completed_status
            else:
                # 如果超过30分钟，且已完成订单已评分或用户是司机，显示未开始订单
                target_order, target_order_status = nearest_future_order, nearest_future_status
        elif future_orders:  # 没有已完成订单，显示最近的未开始订单
            target_order, target_order_status = future_orders[0][0], future_orders[0][1]

    # 优先级3：显示已完成订单（未评分的情况）
    elif completed_orders:
        last_completed_order, last_completed_status = completed_orders[0]

        # 检查用户是否已经评分
        has_rated = False
        if last_completed_order.driver:
            # 查询用户是否已经评分
            existing_rating = DriverRating.query.filter_by(order_id=last_completed_order.order_id, user_username=current_user, driver_username=last_completed_order.driver).first()
            has_rated = existing_rating is not None

            # 如果是司机，视为已评分（司机不需要评价自己）
            if current_user == last_completed_order.driver:
                has_rated = True

        # 如果未评分且当前用户不是司机，显示最后完成的订单
        if not has_rated and current_user != last_completed_order.driver:
            target_order = last_completed_order
            target_order_status = last_completed_status
        else:
            # 已评分或用户是司机，返回无订单
            return jsonify({"code": 404, "message": "未找到相关订单"})

    # 所有情况都没有匹配到
    if not target_order:
        return jsonify({"code": 404, "message": "未找到相关订单"})

    # 返回选中的订单
    return jsonify(
        {
            "code": 200,
            "message": "查询成功",
            "data": {
                "order": {
                    "order_id": target_order.order_id,
                    "user1": target_order.user1,
                    "user2": target_order.user2,
                    "user3": target_order.user3,
                    "user4": target_order.user4,
                    "driver": target_order.driver,
                    "departure": target_order.departure,
                    "destination": target_order.destination,
                    "date": target_order.date.isoformat(),
                    "earliest_departure_time": target_order.earliest_departure_time.isoformat(),
                    "latest_departure_time": target_order.latest_departure_time.isoformat(),
                    "remark": target_order.remark,
                },
                "status": {
                    "status": target_order_status.status,
                    "user1_arrived": target_order_status.user1_arrived,
                    "user2_arrived": target_order_status.user2_arrived,
                    "user3_arrived": target_order_status.user3_arrived,
                    "user4_arrived": target_order_status.user4_arrived,
                    "driver_arrived": target_order_status.driver_arrived,
                },
                "start": {
                    "name": target_order.departure,
                    "lon": posData[target_order.departure][0],
                    "lat": posData[target_order.departure][1]
                },
                "end": {
                    "name": target_order.destination,
                    "lon": posData[target_order.destination][0],
                    "lat": posData[target_order.destination][1]
                }
            },
        }
    )


# 用户/司机确认到达
@app.route("/api/order/confirm-arrival", methods=["POST"])
def confirm_arrival():
    # 获取请求头中的Token
    token = request.headers.get("Authorization")
    if not token:
        return jsonify({"code": 401, "message": "Token缺失"}), 401

    # 检查Token的有效性
    check_result = check_token(token)
    if check_result:
        return check_result  # 如果Token无效，直接返回错误信息

    payload = jwt.decode(token, "secret_key", algorithms=["HS256"])
    current_user = payload["username"]  # 当前用户

    data = request.json
    order_id = data.get("order_id")

    if not order_id:
        return jsonify({"code": 400, "message": "order_id缺失"}), 400

    # 获取订单和订单状态
    order = Order.query.get_or_404(order_id)
    order_status = OrderStatus.query.filter_by(order_id=order_id).first()

    if not order_status:
        return jsonify({"code": 404, "message": "订单状态不存在"}), 404

    # 标记用户到达状态
    is_driver = False
    if current_user == order.driver:
        order_status.driver_arrived = True
        is_driver = True
    elif order.user1 == current_user:
        order_status.user1_arrived = True
    elif order.user2 == current_user:
        order_status.user2_arrived = True
    elif order.user3 == current_user:
        order_status.user3_arrived = True
    elif order.user4 == current_user:
        order_status.user4_arrived = True
    else:
        return jsonify({"code": 403, "message": "您不是该订单的参与者"}), 403

    # 检查是否所有乘客都已到达（不包括司机）
    all_passengers_arrived = True
    if order.user1 and order.user1 != order.driver and not order_status.user1_arrived:
        all_passengers_arrived = False
    if order.user2 and order.user2 != order.driver and not order_status.user2_arrived:
        all_passengers_arrived = False
    if order.user3 and order.user3 != order.driver and not order_status.user3_arrived:
        all_passengers_arrived = False
    if order.user4 and order.user4 != order.driver and not order_status.user4_arrived:
        all_passengers_arrived = False

    # 订单状态保持为0（未开始），直到司机主动点击"开始行程"

    db.session.commit()

    return jsonify(
        {
            "code": 200,
            "message": "确认到达成功",
            "data": {
                "status": order_status.status,
                "user1_arrived": order_status.user1_arrived,
                "user2_arrived": order_status.user2_arrived,
                "user3_arrived": order_status.user3_arrived,
                "user4_arrived": order_status.user4_arrived,
                "driver_arrived": order_status.driver_arrived,
                "all_passengers_arrived": all_passengers_arrived,
            },
        }
    )


# 司机开始行程接口
@app.route("/api/order/start-trip", methods=["POST"])
def start_trip():
    # 获取请求头中的Token
    token = request.headers.get("Authorization")
    if not token:
        return jsonify({"code": 401, "message": "Token缺失"}), 401

    # 检查Token的有效性
    check_result = check_token(token)
    if check_result:
        return check_result  # 如果Token无效，直接返回错误信息

    payload = jwt.decode(token, "secret_key", algorithms=["HS256"])
    current_user = payload["username"]  # 当前用户

    data = request.json
    order_id = data.get("order_id")

    if not order_id:
        return jsonify({"code": 400, "message": "order_id缺失"}), 400

    # 获取订单和订单状态
    order = Order.query.get_or_404(order_id)
    order_status = OrderStatus.query.filter_by(order_id=order_id).first()

    if not order_status:
        return jsonify({"code": 404, "message": "订单状态不存在"}), 404

    # 检查是否是司机
    if current_user != order.driver:
        return jsonify({"code": 403, "message": "只有司机可以开始行程"}), 403

    # 检查司机是否已到达
    if not order_status.driver_arrived:
        return jsonify({"code": 400, "message": "司机尚未确认到达"}), 400

    # 检查所有乘客是否已到达
    all_passengers_arrived = True
    if order.user1 and order.user1 != order.driver and not order_status.user1_arrived:
        all_passengers_arrived = False
    if order.user2 and order.user2 != order.driver and not order_status.user2_arrived:
        all_passengers_arrived = False
    if order.user3 and order.user3 != order.driver and not order_status.user3_arrived:
        all_passengers_arrived = False
    if order.user4 and order.user4 != order.driver and not order_status.user4_arrived:
        all_passengers_arrived = False

    if not all_passengers_arrived:
        return jsonify({"code": 400, "message": "等待所有乘客确认到达"}), 400

    # 更新订单状态为进行中
    order_status.status = 1  # 进行中

    db.session.commit()

    return jsonify({"code": 200, "message": "行程开始成功", "data": {"status": order_status.status}})


# 确认到达目的地
@app.route("/api/order/confirm-destination", methods=["POST"])
def confirm_destination():
    # 获取请求头中的Token
    token = request.headers.get("Authorization")
    if not token:
        return jsonify({"code": 401, "message": "Token缺失"}), 401

    # 检查Token的有效性
    check_result = check_token(token)
    if check_result:
        return check_result  # 如果Token无效，直接返回错误信息

    payload = jwt.decode(token, "secret_key", algorithms=["HS256"])
    current_user = payload["username"]  # 当前用户

    data = request.json
    order_id = data.get("order_id")

    if not order_id:
        return jsonify({"code": 400, "message": "order_id缺失"}), 400

    # 获取订单和订单状态
    order = Order.query.get_or_404(order_id)

    # 检查是否是司机
    user = User.query.filter_by(username=current_user).first()
    is_driver = user and current_user == order.driver

    if is_driver:
        # 司机确认处理
        order_status = OrderStatus.query.filter_by(order_id=order_id).first()
        if not order_status:
            return jsonify({"code": 404, "message": "订单状态不存在"}), 404

        # 只有在进行中状态才能确认到达目的地
        if order_status.status == 1:
            # 设置为"已完成"
            order_status.status = 2  # 已完成
            # 设置完成时间为当前时间
            order_status.completed_at = datetime.datetime.now()
            message = "确认到达目的地成功"
        else:
            message = "状态更新失败，订单状态异常"

        db.session.commit()

        return jsonify({"code": 200, "message": message, "data": {"status": order_status.status, "completed_at": order_status.completed_at.isoformat() if order_status.completed_at else None}})

    return jsonify({"code": 200, "message": "用户确认到达目的地"})


# 提交司机评分
@app.route("/api/order/rate-driver", methods=["POST"])
def rate_driver():
    # 获取请求头中的Token
    token = request.headers.get("Authorization")
    if not token:
        return jsonify({"code": 401, "message": "Token缺失"}), 401

    # 检查Token的有效性
    check_result = check_token(token)
    if check_result:
        return check_result  # 如果Token无效，直接返回错误信息

    payload = jwt.decode(token, "secret_key", algorithms=["HS256"])
    current_user = payload["username"]  # 当前用户

    data = request.json
    order_id = data.get("order_id")
    rating = data.get("rating")

    if not order_id or not rating:
        return jsonify({"code": 400, "message": "订单ID或评分缺失"}), 400

    # 验证评分范围
    try:
        rating_value = float(rating)
        if rating_value < 1 or rating_value > 5:
            return jsonify({"code": 400, "message": "评分必须在1-5之间"}), 400
    except ValueError:
        return jsonify({"code": 400, "message": "评分必须是数字"}), 400

    # 获取订单信息
    order = Order.query.get_or_404(order_id)

    # 确保用户是该订单的参与者
    if current_user != order.user1 and current_user != order.user2 and current_user != order.user3 and current_user != order.user4:
        return jsonify({"code": 403, "message": "您不是该订单的参与者"}), 403

    # 必须先完成支付才能对司机评分
    payment = Payment.query.filter_by(order_id=order_id, passenger_username=current_user).first()
    if not payment or payment.status != "PAID":
        return jsonify({"code": 403, "message": "请先完成支付后再评分"}), 403

    # 找到司机
    driver_user = User.query.filter_by(username=order.driver).first()
    if not driver_user:
        return jsonify({"code": 404, "message": "找不到该订单的司机"}), 404

    # 检查用户是否已经评价过
    existing_rating = DriverRating.query.filter_by(order_id=order_id, user_username=current_user, driver_username=driver_user.username).first()

    # 获取或创建司机平均评分记录
    driver_avg_rating = DriverAverageRating.query.filter_by(driver_username=driver_user.username).first()

    if not driver_avg_rating:
        driver_avg_rating = DriverAverageRating(driver_username=driver_user.username, average_rating=5.0, rating_count=0)
        db.session.add(driver_avg_rating)

    if existing_rating:
        # 如果已有评分，需要更新平均分
        old_rating = existing_rating.rating
        # 更新现有评分记录
        existing_rating.rating = rating_value
        existing_rating.created_at = datetime.datetime.now()

        # 更新平均评分: 从总和中减去旧评分再加上新评分
        if driver_avg_rating.rating_count > 0:
            total_rating = driver_avg_rating.average_rating * driver_avg_rating.rating_count
            total_rating = total_rating - old_rating + rating_value
            driver_avg_rating.average_rating = total_rating / driver_avg_rating.rating_count
    else:
        # 创建新评分
        new_rating = DriverRating(order_id=order_id, driver_username=driver_user.username, user_username=current_user, rating=rating_value)
        db.session.add(new_rating)

        # 更新平均评分: 计算新的平均值
        if driver_avg_rating.rating_count == 0:
            driver_avg_rating.average_rating = rating_value
        else:
            total_rating = driver_avg_rating.average_rating * driver_avg_rating.rating_count
            driver_avg_rating.average_rating = (total_rating + rating_value) / (driver_avg_rating.rating_count + 1)

        # 增加评分次数
        driver_avg_rating.rating_count += 1

    db.session.commit()

    return jsonify({"code": 200, "message": "评分提交成功", "data": {"driver_rating": driver_avg_rating.average_rating}})


# 获取司机评分
@app.route("/api/user/driver-rating/<string:username>", methods=["GET"])
def get_driver_rating(username):
    # 验证该用户是司机
    driver = User.query.filter_by(username=username, usertype=2).first()
    if not driver:
        return jsonify({"code": 404, "message": "找不到该司机"}), 404

    # 获取司机平均评分
    driver_avg_rating = DriverAverageRating.query.filter_by(driver_username=username).first()

    if not driver_avg_rating:
        # 如果没有评分记录，返回默认值
        return jsonify({"code": 200, "message": "查询成功", "data": {"username": username, "rating": 5.0, "rating_count": 0}})

    return jsonify({"code": 200, "message": "查询成功", "data": {"username": username, "rating": driver_avg_rating.average_rating, "rating_count": driver_avg_rating.rating_count}})


# 检查用户是否已对订单司机评分
@app.route("/api/order/check-user-rating", methods=["POST"])
def check_user_rating():
    # 获取请求头中的Token
    token = request.headers.get("Authorization")
    if not token:
        return jsonify({"code": 401, "message": "Token缺失"}), 401

    # 检查Token的有效性
    check_result = check_token(token)
    if check_result:
        return check_result  # 如果Token无效，直接返回错误信息

    payload = jwt.decode(token, "secret_key", algorithms=["HS256"])
    current_user = payload["username"]  # 当前用户

    data = request.json
    order_id = data.get("order_id")
    driver_username = data.get("driver_username")

    if not order_id or not driver_username:
        return jsonify({"code": 400, "message": "订单ID或司机用户名缺失"}), 400

    # 查询是否已经评分
    existing_rating = DriverRating.query.filter_by(order_id=order_id, user_username=current_user, driver_username=driver_username).first()

    return jsonify({"code": 200, "message": "查询成功", "data": {"has_rated": existing_rating is not None}})


@app.route("/api/user/completed-orders", methods=["GET"])
def get_completed_orders():
    # 获取请求头中的Token
    token = request.headers.get("Authorization")
    if not token:
        return jsonify({"code": 401, "message": "Token缺失"}), 401

    # 检查Token的有效性
    check_result = check_token(token)
    if check_result:
        return check_result  # 如果Token无效，直接返回错误信息

    payload = jwt.decode(token, "secret_key", algorithms=["HS256"])
    current_user = payload["username"]  # 当前用户

    # 查询用户参与且已完成的订单
    completed_orders = (
        Order.query.join(OrderStatus, Order.order_id == OrderStatus.order_id)
        .filter(((Order.user1 == current_user) | (Order.user2 == current_user) | (Order.user3 == current_user) | (Order.user4 == current_user) | (Order.driver == current_user)) & (OrderStatus.status == 2))  # 已完成状态
        .order_by(OrderStatus.completed_at.desc())
        .all()
    )

    result = []
    for order in completed_orders:
        order_status = OrderStatus.query.filter_by(order_id=order.order_id).first()
        result.append(
            {
                "order_id": order.order_id,
                "user1": order.user1,
                "user2": order.user2,
                "user3": order.user3,
                "user4": order.user4,
                "driver": order.driver,
                "departure": order.departure,
                "destination": order.destination,
                "date": order.date.isoformat(),
                "earliest_departure_time": order.earliest_departure_time.isoformat(),
                "latest_departure_time": order.latest_departure_time.isoformat(),
                "remark": order.remark,
                "completed_at": order_status.completed_at.isoformat() if order_status.completed_at else None,
            }
        )

    return jsonify({"code": 200, "message": "查询成功", "data": {"list": result}})


# 获取所有未开始的订单
@app.route("/api/orders/not-started", methods=["GET"])
def get_not_started_orders():
    # 先获取所有订单
    orders = Order.query.all()
    result = []

    for order in orders:
        # 获取订单状态
        order_status = OrderStatus.query.filter_by(order_id=order.order_id).first()

        # 如果没有状态记录，创建一个（默认为未开始）
        if not order_status:
            order_status = OrderStatus(order_id=order.order_id, status=0, user1_arrived=False, user2_arrived=False, user3_arrived=False, user4_arrived=False, driver_arrived=False)  # 0表示未开始
            db.session.add(order_status)
            db.session.commit()

        # 只添加未开始的订单
        if order_status.status == 0:
            result.append(
                {
                    "order_id": order.order_id,
                    "user1": order.user1,
                    "user2": order.user2,
                    "user3": order.user3,
                    "user4": order.user4,
                    "driver": order.driver,
                    "departure": order.departure,
                    "destination": order.destination,
                    "date": order.date.isoformat(),
                    "earliest_departure_time": order.earliest_departure_time.isoformat(),
                    "latest_departure_time": order.latest_departure_time.isoformat(),
                    "remark": order.remark,
                }
            )

    return jsonify({"code": 200, "message": "查询成功", "data": {"list": result}})


# 车辆相关接口


# 添加车辆信息
@app.route("/api/vehicle/add", methods=["POST"])
def add_vehicle():
    # 获取请求头中的Token
    token = request.headers.get("Authorization")
    if not token:
        return jsonify({"code": 401, "message": "Token缺失"}), 401

    # 检查Token的有效性
    check_result = check_token(token)
    if check_result:
        return check_result

    payload = jwt.decode(token, "secret_key", algorithms=["HS256"])
    current_user = payload["username"]

    # 验证用户是司机
    user = User.query.filter_by(username=current_user).first()
    if not user or user.usertype != 2:
        return jsonify({"code": 403, "message": "只有司机可以添加车辆信息"}), 403

    data = request.json
    license_plate = data.get("license_plate")
    brand = data.get("brand")
    model = data.get("model")
    color = data.get("color")
    seat_count = data.get("seat_count", 4)

    if not all([license_plate, brand, model, color]):
        return jsonify({"code": 400, "message": "车辆信息不完整"}), 400

    # 检查车牌号是否已存在
    existing_vehicle = Vehicle.query.filter_by(license_plate=license_plate).first()
    if existing_vehicle:
        return jsonify({"code": 409, "message": "车牌号已存在"}), 409

    # 创建车辆记录
    vehicle = Vehicle(driver_username=current_user, license_plate=license_plate, brand=brand, model=model, color=color, seat_count=seat_count)

    db.session.add(vehicle)
    db.session.commit()

    return jsonify({"code": 201, "message": "车辆信息添加成功", "data": {"vehicle_id": vehicle.vehicle_id, "license_plate": vehicle.license_plate, "brand": vehicle.brand, "model": vehicle.model, "color": vehicle.color, "seat_count": vehicle.seat_count, "is_verified": vehicle.is_verified}})


# 获取司机的车辆信息
@app.route("/api/vehicle/my-vehicles", methods=["GET"])
def get_my_vehicles():
    # 获取请求头中的Token
    token = request.headers.get("Authorization")
    if not token:
        return jsonify({"code": 401, "message": "Token缺失"}), 401

    # 检查Token的有效性
    check_result = check_token(token)
    if check_result:
        return check_result

    payload = jwt.decode(token, "secret_key", algorithms=["HS256"])
    current_user = payload["username"]

    # 验证用户是司机
    user = User.query.filter_by(username=current_user).first()
    if not user or user.usertype != 2:
        return jsonify({"code": 403, "message": "只有司机可以查看车辆信息"}), 403

    vehicles = Vehicle.query.filter_by(driver_username=current_user).all()

    vehicle_list = []
    for vehicle in vehicles:
        vehicle_list.append({"vehicle_id": vehicle.vehicle_id, "license_plate": vehicle.license_plate, "brand": vehicle.brand, "model": vehicle.model, "color": vehicle.color, "seat_count": vehicle.seat_count, "is_verified": vehicle.is_verified, "created_at": vehicle.created_at.isoformat()})

    return jsonify({"code": 200, "message": "查询成功", "data": {"list": vehicle_list}})


# 更新车辆信息
@app.route("/api/vehicle/update/<int:vehicle_id>", methods=["PUT"])
def update_vehicle(vehicle_id):
    # 获取请求头中的Token
    token = request.headers.get("Authorization")
    if not token:
        return jsonify({"code": 401, "message": "Token缺失"}), 401

    # 检查Token的有效性
    check_result = check_token(token)
    if check_result:
        return check_result

    payload = jwt.decode(token, "secret_key", algorithms=["HS256"])
    current_user = payload["username"]

    # 查找车辆
    vehicle = Vehicle.query.get_or_404(vehicle_id)

    # 验证车辆所有权
    if vehicle.driver_username != current_user:
        return jsonify({"code": 403, "message": "无权修改此车辆信息"}), 403

    data = request.json

    # 更新车辆信息
    if "brand" in data:
        vehicle.brand = data["brand"]
    if "model" in data:
        vehicle.model = data["model"]
    if "color" in data:
        vehicle.color = data["color"]
    if "seat_count" in data:
        vehicle.seat_count = data["seat_count"]
    if "license_plate" in data:
        # 检查新车牌号是否已存在
        existing_vehicle = Vehicle.query.filter_by(license_plate=data["license_plate"]).first()
        if existing_vehicle and existing_vehicle.vehicle_id != vehicle_id:
            return jsonify({"code": 409, "message": "车牌号已存在"}), 409
        vehicle.license_plate = data["license_plate"]

    db.session.commit()

    return jsonify({"code": 200, "message": "车辆信息更新成功", "data": {"vehicle_id": vehicle.vehicle_id, "license_plate": vehicle.license_plate, "brand": vehicle.brand, "model": vehicle.model, "color": vehicle.color, "seat_count": vehicle.seat_count, "is_verified": vehicle.is_verified}})


# 删除车辆信息
@app.route("/api/vehicle/delete/<int:vehicle_id>", methods=["DELETE"])
def delete_vehicle(vehicle_id):
    # 获取请求头中的Token
    token = request.headers.get("Authorization")
    if not token:
        return jsonify({"code": 401, "message": "Token缺失"}), 401

    # 检查Token的有效性
    check_result = check_token(token)
    if check_result:
        return check_result

    payload = jwt.decode(token, "secret_key", algorithms=["HS256"])
    current_user = payload["username"]

    # 查找车辆
    vehicle = Vehicle.query.get_or_404(vehicle_id)

    # 验证车辆所有权
    if vehicle.driver_username != current_user:
        return jsonify({"code": 403, "message": "无权删除此车辆信息"}), 403

    db.session.delete(vehicle)
    db.session.commit()

    return jsonify({"code": 200, "message": "车辆信息删除成功"})


# 优惠券相关接口


# 创建优惠券（管理员功能）
@app.route("/api/coupon/create", methods=["POST"])
def create_coupon():
    data = request.json

    required_fields = ["coupon_name", "discount_type", "discount_value", "start_date", "end_date"]
    if not all(field in data for field in required_fields):
        return jsonify({"code": 400, "message": "优惠券信息不完整"}), 400

    # 验证折扣类型
    if data["discount_type"] not in ["percentage", "fixed"]:
        return jsonify({"code": 400, "message": "折扣类型必须是percentage或fixed"}), 400

    try:
        start_date = datetime.datetime.strptime(data["start_date"], "%Y-%m-%d").date()
        end_date = datetime.datetime.strptime(data["end_date"], "%Y-%m-%d").date()

        if start_date >= end_date:
            return jsonify({"code": 400, "message": "结束日期必须晚于开始日期"}), 400

        coupon = Coupon(coupon_name=data["coupon_name"], discount_type=data["discount_type"], discount_value=data["discount_value"], min_amount=data.get("min_amount", 0), start_date=start_date, end_date=end_date, usage_limit=data.get("usage_limit", 1), is_active=data.get("is_active", True))

        db.session.add(coupon)
        db.session.commit()

        return jsonify(
            {
                "code": 201,
                "message": "优惠券创建成功",
                "data": {
                    "coupon_id": coupon.coupon_id,
                    "coupon_name": coupon.coupon_name,
                    "discount_type": coupon.discount_type,
                    "discount_value": float(coupon.discount_value),
                    "min_amount": float(coupon.min_amount),
                    "start_date": coupon.start_date.isoformat(),
                    "end_date": coupon.end_date.isoformat(),
                    "usage_limit": coupon.usage_limit,
                    "is_active": coupon.is_active,
                },
            }
        )
    except ValueError:
        return jsonify({"code": 400, "message": "日期格式错误"}), 400


# 获取所有可用优惠券
@app.route("/api/coupon/available", methods=["GET"])
def get_available_coupons():
    current_date = datetime.datetime.now().date()

    # 查询当前有效的优惠券
    coupons = Coupon.query.filter(Coupon.is_active == True, Coupon.start_date <= current_date, Coupon.end_date >= current_date).all()

    coupon_list = []
    for coupon in coupons:
        coupon_list.append(
            {
                "coupon_id": coupon.coupon_id,
                "coupon_name": coupon.coupon_name,
                "discount_type": coupon.discount_type,
                "discount_value": float(coupon.discount_value),
                "min_amount": float(coupon.min_amount),
                "start_date": coupon.start_date.isoformat(),
                "end_date": coupon.end_date.isoformat(),
                "usage_limit": coupon.usage_limit,
            }
        )

    return jsonify({"code": 200, "message": "查询成功", "data": {"list": coupon_list}})


# 用户领取优惠券
@app.route("/api/coupon/claim/<int:coupon_id>", methods=["POST"])
def claim_coupon(coupon_id):
    # 获取请求头中的Token
    token = request.headers.get("Authorization")
    if not token:
        return jsonify({"code": 401, "message": "Token缺失"}), 401

    # 检查Token的有效性
    check_result = check_token(token)
    if check_result:
        return check_result

    payload = jwt.decode(token, "secret_key", algorithms=["HS256"])
    current_user = payload["username"]

    # 检查优惠券是否存在且有效
    coupon = Coupon.query.get_or_404(coupon_id)
    current_date = datetime.datetime.now().date()

    if not coupon.is_active:
        return jsonify({"code": 400, "message": "优惠券已失效"}), 400

    if coupon.start_date > current_date or coupon.end_date < current_date:
        return jsonify({"code": 400, "message": "优惠券不在有效期内"}), 400

    # 检查用户是否已经领取过该优惠券
    existing_user_coupon = UserCoupon.query.filter_by(username=current_user, coupon_id=coupon_id).first()

    if existing_user_coupon:
        return jsonify({"code": 409, "message": "您已经领取过该优惠券"}), 409

    # 创建用户优惠券记录
    user_coupon = UserCoupon(username=current_user, coupon_id=coupon_id)

    db.session.add(user_coupon)
    db.session.commit()

    return jsonify({"code": 200, "message": "优惠券领取成功", "data": {"coupon_name": coupon.coupon_name, "discount_type": coupon.discount_type, "discount_value": float(coupon.discount_value)}})


# 获取用户的优惠券
@app.route("/api/coupon/my-coupons", methods=["GET"])
def get_my_coupons():
    # 获取请求头中的Token
    token = request.headers.get("Authorization")
    if not token:
        return jsonify({"code": 401, "message": "Token缺失"}), 401

    # 检查Token的有效性
    check_result = check_token(token)
    if check_result:
        return check_result

    payload = jwt.decode(token, "secret_key", algorithms=["HS256"])
    current_user = payload["username"]

    # 查询用户的优惠券
    user_coupons = db.session.query(UserCoupon, Coupon).join(Coupon, UserCoupon.coupon_id == Coupon.coupon_id).filter(UserCoupon.username == current_user).all()

    coupon_list = []
    current_date = datetime.datetime.now().date()

    for user_coupon, coupon in user_coupons:
        # 判断优惠券状态
        is_expired = coupon.end_date < current_date
        is_used_up = user_coupon.used_count >= coupon.usage_limit
        can_use = not is_expired and not is_used_up and coupon.is_active

        coupon_list.append(
            {
                "coupon_id": coupon.coupon_id,
                "coupon_name": coupon.coupon_name,
                "discount_type": coupon.discount_type,
                "discount_value": float(coupon.discount_value),
                "min_amount": float(coupon.min_amount),
                "start_date": coupon.start_date.isoformat(),
                "end_date": coupon.end_date.isoformat(),
                "usage_limit": coupon.usage_limit,
                "used_count": user_coupon.used_count,
                "obtained_at": user_coupon.obtained_at.isoformat(),
                "can_use": can_use,
                "is_expired": is_expired,
            }
        )

    return jsonify({"code": 200, "message": "查询成功", "data": {"list": coupon_list}})


# 使用优惠券（在订单支付时调用）
@app.route("/api/coupon/use/<int:coupon_id>", methods=["POST"])
def use_coupon(coupon_id):
    # 获取请求头中的Token
    token = request.headers.get("Authorization")
    if not token:
        return jsonify({"code": 401, "message": "Token缺失"}), 401

    # 检查Token的有效性
    check_result = check_token(token)
    if check_result:
        return check_result

    payload = jwt.decode(token, "secret_key", algorithms=["HS256"])
    current_user = payload["username"]

    data = request.json
    order_amount = data.get("amount", 0)

    # 查找用户的优惠券
    user_coupon = UserCoupon.query.filter_by(username=current_user, coupon_id=coupon_id).first()

    if not user_coupon:
        return jsonify({"code": 404, "message": "您没有该优惠券"}), 404

    # 获取优惠券信息
    coupon = Coupon.query.get_or_404(coupon_id)
    current_date = datetime.datetime.now().date()

    # 验证优惠券状态
    if not coupon.is_active:
        return jsonify({"code": 400, "message": "优惠券已失效"}), 400

    if coupon.end_date < current_date:
        return jsonify({"code": 400, "message": "优惠券已过期"}), 400

    if user_coupon.used_count >= coupon.usage_limit:
        return jsonify({"code": 400, "message": "优惠券使用次数已达上限"}), 400

    if order_amount < coupon.min_amount:
        return jsonify({"code": 400, "message": f"订单金额不满足最低消费要求({coupon.min_amount}元)"}), 400

    # 计算折扣金额
    if coupon.discount_type == "percentage":
        discount_amount = order_amount * (float(coupon.discount_value) / 100)
    else:  # fixed
        discount_amount = float(coupon.discount_value)

    # 确保折扣金额不超过订单金额
    discount_amount = min(discount_amount, order_amount)
    final_amount = order_amount - discount_amount

    # 增加使用次数
    user_coupon.used_count += 1
    db.session.commit()

    return jsonify({"code": 200, "message": "优惠券使用成功", "data": {"original_amount": order_amount, "discount_amount": discount_amount, "final_amount": final_amount, "coupon_name": coupon.coupon_name}})


# ====================== 支付宝沙箱支付系统 ======================
# 业务约束：
#   1. 只有订单状态为已完成 (status=2) 时，乘客才可发起支付。
#   2. 同一 (order_id, passenger_username) 只允许一次成功支付。
#   3. 司机本人不能为自己的订单支付。
#   4. 必须先支付成功才能调用 /api/order/rate-driver 对司机评分。
#
# 新增内容：
#   * Payment 表加入 out_trade_no / alipay_trade_no / pay_url / updated_at 字段。
#   * status 取值：CREATED → PENDING → PAID；或 FAILED / CLOSED。
#     旧的 UNPAID / PAID 记录依然兼容（rate-driver 仍以 status == 'PAID' 判断）。
#   * /api/payment/pay 不再直接置 PAID，而是调用支付宝沙箱生成 pay_url，
#     前端打开浏览器付款；
#   * /api/payment/status 查询本地支付状态，
#   * /api/payment/query  主动查询支付宝交易状态并刷新本地记录，
#     /api/payment/notify 接收异步通知（验签后更新），
#     /api/payment/return 渲染同步返回页。

class Payment(db.Model):
    __tablename__ = "payment"
    payment_id = db.Column(db.Integer, primary_key=True, autoincrement=True)
    order_id = db.Column(db.Integer, db.ForeignKey("orders.order_id"), nullable=False)
    passenger_username = db.Column(db.String(20), db.ForeignKey("user.username"), nullable=False)
    driver_username = db.Column(db.String(20), db.ForeignKey("user.username"), nullable=False)
    amount = db.Column(db.Numeric(10, 2), nullable=False)
    # 支付状态：CREATED / PENDING / PAID / FAILED / CLOSED（兼容历史值 UNPAID）
    status = db.Column(db.String(20), nullable=False, default="CREATED")
    # 本系统支付单号，唯一；用于和支付宝交互
    out_trade_no = db.Column(db.String(64), unique=True, nullable=True)
    # 支付宝交易号，支付成功后回写
    alipay_trade_no = db.Column(db.String(64), nullable=True)
    # 支付宝返回的网页支付跳转链接（含签名的 querystring 完整 URL）
    pay_url = db.Column(db.Text, nullable=True)
    paid_at = db.Column(db.DateTime, nullable=True)
    created_at = db.Column(db.DateTime, default=datetime.datetime.now)
    updated_at = db.Column(
        db.DateTime,
        default=datetime.datetime.now,
        onupdate=datetime.datetime.now,
    )

    __table_args__ = (
        db.UniqueConstraint("order_id", "passenger_username", name="uq_payment_order_passenger"),
    )


# ------ 内部工具函数 ------

def _make_out_trade_no(order_id: int) -> str:
    """生成本系统唯一支付单号。格式：WW{order_id}-{时间戳}-{8位UUID}，<=64位 ASCII。"""
    ts = datetime.datetime.now().strftime("%Y%m%d%H%M%S")
    short = uuid.uuid4().hex[:8]
    return f"WW{order_id}-{ts}-{short}"


def _alipay_status_to_local(trade_status: str) -> str:
    """支付宝 trade_status → 本系统 Payment.status。"""
    mapping = {
        "TRADE_SUCCESS": "PAID",
        "TRADE_FINISHED": "PAID",
        "WAIT_BUYER_PAY": "PENDING",
        "TRADE_CLOSED": "CLOSED",
    }
    return mapping.get(trade_status or "", "CREATED")


def _payment_to_dict(payment: "Payment") -> dict:
    """统一序列化 Payment，给前端返回。"""
    return {
        "payment_id": payment.payment_id,
        "order_id": payment.order_id,
        "passenger_username": payment.passenger_username,
        "driver_username": payment.driver_username,
        "amount": float(payment.amount) if payment.amount is not None else None,
        "status": payment.status,
        "has_paid": payment.status == "PAID",
        "out_trade_no": payment.out_trade_no,
        "alipay_trade_no": payment.alipay_trade_no,
        "pay_url": payment.pay_url,
        "paid_at": payment.paid_at.isoformat() if payment.paid_at else None,
    }


# ------ 共用：JWT 校验，复用既有 check_token ------

def _require_login():
    """
    通用登录校验：成功返回 (None, current_user)；失败返回 (response, None)。
    所有 /api/payment/* 接口除 notify/return 之外都走它。
    """
    token = request.headers.get("Authorization")
    if not token:
        return (jsonify({"code": 401, "message": "Token缺失"}), 401), None
    check_result = check_token(token)
    if check_result:
        return check_result, None
    payload = jwt.decode(token, "secret_key", algorithms=["HS256"])
    return None, payload["username"]


# ------ /api/payment/pay：创建支付宝沙箱支付订单 ------
@app.route("/api/payment/pay", methods=["POST"])
def pay_order():
    """
    创建支付宝沙箱支付订单。
    请求体兼容旧接口：{ order_id: int, amount: number }
    成功返回 pay_url 等字段，由前端打开支付宝网页完成付款。
    """
    err, current_user = _require_login()
    if err:
        return err

    data = request.json or {}
    order_id = data.get("order_id")
    amount = data.get("amount")

    if order_id is None or amount is None:
        return jsonify({"code": 400, "message": "order_id 或 amount 缺失"}), 400
    
    # 金额校验
    try:
        amount_value = float(amount)
    except (TypeError, ValueError):
        return jsonify({"code": 400, "message": "支付金额必须是数字"}), 400
    if amount_value <= 0:
        return jsonify({"code": 400, "message": "支付金额必须大于0"}), 400

    # 订单必须存在
    order = Order.query.get_or_404(order_id)

    # 订单必须已完成
    order_status = OrderStatus.query.filter_by(order_id=order_id).first()
    if not order_status:
        return jsonify({"code": 404, "message": "订单状态不存在"}), 404
    if order_status.status != 2:
        return jsonify({"code": 400, "message": "订单尚未完成，无法支付"}), 400

    if not order.driver:
        return jsonify({"code": 400, "message": "订单暂无司机，无需支付"}), 400
    if current_user not in [order.user1, order.user2, order.user3, order.user4]:
        return jsonify({"code": 403, "message": "您不是该订单的乘客"}), 403
    if current_user == order.driver:
        return jsonify({"code": 403, "message": "司机不能为自己支付"}), 403

    payment = Payment.query.filter_by(
        order_id=order_id, passenger_username=current_user
    ).first()
    if payment and payment.status == "PAID":
        return jsonify({"code": 409, "message": "您已完成支付，请勿重复支付"}), 409

    # 加载支付宝客户端
    try:
        client = get_alipay_client()
        gateway = get_gateway()
    except AlipayConfigError as exc:
        return jsonify({"code": 500, "message": f"支付宝配置错误：{exc}"}), 500
    except Exception as exc:  # pragma: no cover
        _log.exception("加载支付宝客户端失败")
        return jsonify({"code": 500, "message": "支付通道初始化失败"}), 500

    out_trade_no = _make_out_trade_no(order_id)
    subject = f"星途拼车订单 #{order_id}"

    # return_url / notify_url：优先环境变量，否则按当前 host 拼
    host_root = request.host_url.rstrip("/")
    return_url = get_return_url(default=f"{host_root}/api/payment/return")
    notify_url = get_notify_url(default=f"{host_root}/api/payment/notify")

    # 调用支付宝沙箱网页支付（page_pay）。注意金额必须是字符串、两位小数。
    try:
        order_string = client.api_alipay_trade_page_pay(
            out_trade_no=out_trade_no,
            total_amount=format(amount_value, ".2f"),
            subject=subject,
            return_url=return_url,
            notify_url=notify_url,
        )
    except Exception as exc:  # pragma: no cover
        _log.exception("调用支付宝 page_pay 失败")
        return jsonify({"code": 500, "message": f"创建支付订单失败：{exc}"}), 500

    pay_url = f"{gateway}?{order_string}"

    # 复用旧行或新建行
    if payment is None:
        payment = Payment(
            order_id=order_id,
            passenger_username=current_user,
            driver_username=order.driver,
            amount=amount_value,
            status="CREATED",
            out_trade_no=out_trade_no,
            pay_url=pay_url,
        )
        db.session.add(payment)
    else:
        payment.amount = amount_value
        payment.driver_username = order.driver
        payment.status = "CREATED"
        payment.out_trade_no = out_trade_no
        payment.pay_url = pay_url
        payment.alipay_trade_no = None
        payment.paid_at = None

    db.session.commit()

    return jsonify({
        "code": 200,
        "message": "支付订单创建成功",
        "data": _payment_to_dict(payment),
    })


# ------ /api/payment/status：查当前用户对订单的本地支付状态 ------
@app.route("/api/payment/status", methods=["POST"])
def payment_status():
    err, current_user = _require_login()
    if err:
        return err

    data = request.json or {}
    order_id = data.get("order_id")
    if order_id is None:
        return jsonify({"code": 400, "message": "order_id 缺失"}), 400

    payment = Payment.query.filter_by(
        order_id=order_id, passenger_username=current_user
    ).first()
    if not payment:
        return jsonify({
            "code": 200,
            "message": "查询成功",
            "data": {
                "order_id": order_id,
                "has_paid": False,
                "status": "UNPAID",
                "amount": None,
                "out_trade_no": None,
                "alipay_trade_no": None,
                "pay_url": None,
                "paid_at": None,
            },
        })
    return jsonify({
        "code": 200,
        "message": "查询成功",
        "data": _payment_to_dict(payment),
    })


# ------ /api/payment/query：主动调用支付宝交易查询接口 ------
@app.route("/api/payment/query", methods=["POST", "GET"])
def payment_query():
    """
    主动查询支付宝沙箱交易状态，并把本地 Payment 同步刷新。
    入参：{ order_id?: int, out_trade_no?: str }
    至少要给一个；当 order_id 给定时按当前用户（乘客）定位 Payment 行。
    """
    err, current_user = _require_login()
    if err:
        return err

    if request.method == "GET":
        data = request.args
    else:
        data = request.json or {}
    order_id = data.get("order_id")
    out_trade_no = data.get("out_trade_no")

    payment = None
    if out_trade_no:
        payment = Payment.query.filter_by(out_trade_no=out_trade_no).first()
        if payment and payment.passenger_username != current_user:
            return jsonify({"code": 403, "message": "无权查询该支付单"}), 403
    elif order_id is not None:
        payment = Payment.query.filter_by(
            order_id=order_id, passenger_username=current_user
        ).first()

    if not payment:
        return jsonify({"code": 404, "message": "支付单不存在，请先发起支付"}), 404

    # 已是终态就不再查支付宝
    if payment.status == "PAID":
        return jsonify({
            "code": 200, "message": "已支付", "data": _payment_to_dict(payment)
        })

    if not payment.out_trade_no:
        return jsonify({"code": 400, "message": "本地支付单缺失 out_trade_no"}), 400

    try:
        client = get_alipay_client()
        result = client.api_alipay_trade_query(out_trade_no=payment.out_trade_no)
    except AlipayConfigError as exc:
        return jsonify({"code": 500, "message": f"支付宝配置错误：{exc}"}), 500
    except Exception as exc:  # pragma: no cover
        _log.exception("调用支付宝交易查询失败")
        return jsonify({"code": 502, "message": f"查询支付宝失败：{exc}"}), 502

    # python-alipay-sdk 把外层 alipay_trade_query_response 直接展开成 result
    code_str = str(result.get("code", ""))
    if code_str != "10000":
        # 沙箱查询失败：通常是单号不存在（用户还没付款生成支付宝侧交易）
        # 这种情况保持本地 status 不变，正常返回，供前端继续轮询
        return jsonify({
            "code": 200,
            "message": result.get("sub_msg") or result.get("msg") or "支付宝未返回交易",
            "data": _payment_to_dict(payment),
        })

    trade_status = result.get("trade_status", "")
    new_status = _alipay_status_to_local(trade_status)
    alipay_trade_no = result.get("trade_no")

    changed = False
    if new_status == "PAID" and payment.status != "PAID":
        payment.status = "PAID"
        payment.alipay_trade_no = alipay_trade_no
        payment.paid_at = datetime.datetime.now()
        changed = True
    elif new_status != payment.status and new_status in ("PENDING", "CLOSED"):
        payment.status = new_status
        if alipay_trade_no:
            payment.alipay_trade_no = alipay_trade_no
        changed = True

    if changed:
        db.session.commit()

    return jsonify({
        "code": 200,
        "message": "查询成功",
        "data": _payment_to_dict(payment),
    })


# ------ /api/payment/notify：支付宝异步通知 ------
@app.route("/api/payment/notify", methods=["POST"])
def payment_notify():
    """
    支付宝异步通知。
    - 验签：用 alipay_public_key
    - 验签通过且 trade_status ∈ {TRADE_SUCCESS, TRADE_FINISHED} 时更新本地 Payment
    - 必须返回纯文本 success，否则支付宝会重试
    """
    raw = request.form.to_dict()
    if not raw:
        return "failure"

    signature = raw.pop("sign", None)
    raw.pop("sign_type", None)
    if not signature:
        return "failure"

    try:
        client = get_alipay_client()
        verified = client.verify(raw, signature)
    except Exception:  # pragma: no cover
        _log.exception("支付宝通知验签出错")
        return "failure"

    if not verified:
        return "failure"

    out_trade_no = raw.get("out_trade_no")
    trade_status = raw.get("trade_status", "")
    alipay_trade_no = raw.get("trade_no")
    if not out_trade_no:
        return "failure"

    payment = Payment.query.filter_by(out_trade_no=out_trade_no).first()
    if not payment:
        # 不存在的单号也返回 success，避免支付宝重复推送（log 中已记录）
        return "success"

    if trade_status in ("TRADE_SUCCESS", "TRADE_FINISHED") and payment.status != "PAID":
        payment.status = "PAID"
        payment.alipay_trade_no = alipay_trade_no
        payment.paid_at = datetime.datetime.now()
        db.session.commit()
    elif trade_status == "TRADE_CLOSED" and payment.status not in ("PAID", "CLOSED"):
        payment.status = "CLOSED"
        db.session.commit()

    return "success"


# ------ /api/payment/return：支付宝同步跳转返回页 ------
@app.route("/api/payment/return", methods=["GET"])
def payment_return():
    """支付宝同步返回页面，仅做提示，不在此处更新支付状态（不安全）。"""
    html = (
        "<!DOCTYPE html><html lang='zh-CN'><head><meta charset='utf-8'>"
        "<meta name='viewport' content='width=device-width, initial-scale=1'>"
        "<title>支付返回</title>"
        "<style>body{font-family:sans-serif;text-align:center;padding:48px;color:#333}"
        "h1{color:#0089FF}p{color:#666}</style></head><body>"
        "<h1>支付提交完成</h1>"
        "<p>请回到拼车 App，下拉刷新或点击“点此刷新”按钮以更新订单状态。</p>"
        "<p>本页面可以关闭。</p>"
        "</body></html>"
    )
    response = make_response(html, 200)
    response.headers["Content-Type"] = "text/html; charset=utf-8"
    return response


def init_db():
    # Run after all db.Model classes are defined so every table is registered.
    with app.app_context():
        db.create_all()


if __name__ == '__main__':
    init_db()
    app.run(debug=True, port=8443, use_reloader=False)
    # app.run(host='0.0.0.0', port=8443, debug=False)