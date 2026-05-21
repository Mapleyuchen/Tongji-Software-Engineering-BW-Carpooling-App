"""
支付宝沙箱配置加载与客户端工厂。

设计要点：
1. 配置来源优先级：环境变量 > 项目根目录 aliapi.txt > 报错。
2. 不把私钥/公钥打印到日志或返回给前端。
3. 给 python-alipay-sdk 提供 PEM 头尾，避免常见的 "key error" 问题。
4. 显式覆盖 SDK 默认网关到 aliapi.txt 指定的新沙箱网关
   (https://openapi-sandbox.dl.alipaydev.com/gateway.do)。
"""

from __future__ import annotations

import os
import threading
from pathlib import Path
from typing import Optional

_ALIPAY_TXT_NAME = "aliapi.txt"
_DEFAULT_GATEWAY = "https://openapi-sandbox.dl.alipaydev.com/gateway.do"


class AlipayConfigError(RuntimeError):
    """支付宝配置缺失或加载失败时抛出。"""


def _project_root() -> Path:
    """返回项目根目录（aliapi.txt 所在目录）。"""
    # 本文件位于 white_back_end/，项目根在其父目录。
    return Path(__file__).resolve().parent.parent


def _parse_aliapi_txt(path: Path) -> dict:
    """
    解析 aliapi.txt。文件格式约定：
      第 1 行 标题（例如 "APPID"）
      第 2 行 对应值（base64 或字符串）
      空行
      ...
    """
    section_map = {
        "APPID": "app_id",
        "应用公钥": "app_public_key",
        "应用私钥（非JAVA语言）": "app_private_key",
        "支付宝公钥": "alipay_public_key",
        "支付宝网关地址": "gateway",
    }
    result: dict = {}
    with path.open("r", encoding="utf-8") as fh:
        lines = [line.rstrip("\r\n") for line in fh.readlines()]
    i = 0
    while i < len(lines):
        title = lines[i].strip()
        if not title:
            i += 1
            continue
        if title in section_map:
            value_parts = []
            j = i + 1
            while j < len(lines) and lines[j].strip():
                value_parts.append(lines[j].strip())
                j += 1
            result[section_map[title]] = "".join(value_parts)
            i = j
        else:
            i += 1
    return result


def _wrap_pem(raw_key: str, label: str) -> str:
    """
    把 aliapi.txt 中无头尾的 base64 字符串包装成标准 PEM 格式。
    label 例如 "RSA PRIVATE KEY" 或 "PUBLIC KEY"。
    """
    raw_key = raw_key.strip().replace("\n", "").replace(" ", "")
    # 每 64 字符插入换行，符合 PEM 规范
    body = "\n".join(raw_key[k : k + 64] for k in range(0, len(raw_key), 64))
    return f"-----BEGIN {label}-----\n{body}\n-----END {label}-----\n"


_config_lock = threading.Lock()
_cached_config: Optional[dict] = None
_cached_client = None  # AliPay 实例


def load_alipay_config() -> dict:
    """读取并校验支付宝配置，返回 dict（不含明文私钥日志）。"""
    global _cached_config
    if _cached_config is not None:
        return _cached_config

    with _config_lock:
        if _cached_config is not None:
            return _cached_config

        # 1) 先尝试从 aliapi.txt 加载
        cfg: dict = {}
        txt_path = _project_root() / _ALIPAY_TXT_NAME

        print("[DEBUG] === 对吗？ ===", flush=True)
        
        if txt_path.is_file():
            try:
                cfg = _parse_aliapi_txt(txt_path)
                print("[DEBUG] === 哦对的对的 ===", flush=True)
            except Exception as exc:  # pragma: no cover - 解析容错
                raise AlipayConfigError(
                    f"读取 {_ALIPAY_TXT_NAME} 失败：{exc}"
                ) from exc

        # 2) 环境变量覆盖（如果设置了同名变量）
        env_map = {
            "app_id": "ALIPAY_APP_ID",
            "app_private_key": "ALIPAY_APP_PRIVATE_KEY",
            "alipay_public_key": "ALIPAY_ALIPAY_PUBLIC_KEY",
            "gateway": "ALIPAY_GATEWAY",
        }
        for key, env_name in env_map.items():
            env_value = os.environ.get(env_name)
            if env_value:
                cfg[key] = env_value.strip()

        # 3) return_url / notify_url 由环境变量提供（公网隧道场景）
        cfg["return_url"] = os.environ.get("ALIPAY_RETURN_URL", "").strip() or None
        cfg["notify_url"] = os.environ.get("ALIPAY_NOTIFY_URL", "").strip() or None

        # 4) 网关默认值兜底
        cfg["gateway"] = cfg.get("gateway") or _DEFAULT_GATEWAY

        # 5) 必填字段校验
        required = ["app_id", "app_private_key", "alipay_public_key", "gateway"]
        missing = [k for k in required if not cfg.get(k)]
        if missing:
            raise AlipayConfigError(
                "支付宝配置缺失字段: " + ", ".join(missing) +
                "。请确认项目根目录存在 aliapi.txt 或设置相应环境变量。"
            )

        # 6) 把无头尾的 base64 字符串包装为 PEM，方便 SDK 直接使用
        if "-----BEGIN" not in cfg["app_private_key"]:
            cfg["app_private_key_pem"] = _wrap_pem(
                cfg["app_private_key"], "RSA PRIVATE KEY"
            )
        else:
            cfg["app_private_key_pem"] = cfg["app_private_key"]
        if "-----BEGIN" not in cfg["alipay_public_key"]:
            cfg["alipay_public_key_pem"] = _wrap_pem(
                cfg["alipay_public_key"], "PUBLIC KEY"
            )
        else:
            cfg["alipay_public_key_pem"] = cfg["alipay_public_key"]

        _cached_config = cfg
        return cfg


def get_alipay_client():
    """
    返回 python-alipay-sdk 的 AliPay 实例（懒加载 + 单例）。
    sign_type 固定 RSA2，debug=True 走沙箱。
    """
    global _cached_client
    if _cached_client is not None:
        return _cached_client
    
    cfg = load_alipay_config()

    with _config_lock:
        if _cached_client is not None:
            return _cached_client

        # 延迟 import，避免模块加载阶段报错（如果未安装 python-alipay-sdk）
        from alipay import AliPay  # type: ignore

        client = AliPay(
            appid=cfg["app_id"],
            app_notify_url=cfg.get("notify_url"),
            app_private_key_string=cfg["app_private_key_pem"],
            alipay_public_key_string=cfg["alipay_public_key_pem"],
            sign_type="RSA2",
            debug=True,  # 沙箱
        )

        # python-alipay-sdk 默认 debug=True 时网关是
        # https://openapi.alipaydev.com/gateway.do
        # 这里强制覆盖为 aliapi.txt 指定的新沙箱网关。
        try:
            client._gateway = cfg["gateway"]
        except Exception:  # pragma: no cover
            setattr(client, "_gateway", cfg["gateway"])

        _cached_client = client
        return client


def get_gateway() -> str:
    """返回支付宝网关 URL（用于拼接 page_pay 跳转链接）。"""
    return load_alipay_config()["gateway"]


def get_return_url(default: Optional[str] = None) -> Optional[str]:
    """
    返回支付宝同步跳转地址。
    若环境变量未提供，则用 default（通常是 request.host_url + 'api/payment/return'）。
    """
    cfg = load_alipay_config()
    return cfg.get("return_url") or default


def get_notify_url(default: Optional[str] = None) -> Optional[str]:
    """
    返回支付宝异步通知地址。
    若环境变量未提供，则用 default（通常是 request.host_url + 'api/payment/notify'）。
    注意：localhost 地址支付宝服务器访问不到，需要通过 ngrok/cpolar 等隧道暴露。
    """
    cfg = load_alipay_config()
    return cfg.get("notify_url") or default


def reset_cache_for_tests() -> None:
    """单元测试用：清空配置/客户端缓存。生产代码不要调用。"""
    global _cached_config, _cached_client
    with _config_lock:
        _cached_config = None
        _cached_client = None
