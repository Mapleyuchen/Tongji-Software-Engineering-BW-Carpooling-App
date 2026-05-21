"""
支付宝沙箱配置加载与客户端工厂。

设计要点：
1. 配置来源：white_back_end/config/alipay_sandbox.json（缺失即报错）。
2. 不把私钥/公钥打印到日志或返回给前端。
3. 给 python-alipay-sdk 提供 PEM 头尾，避免常见的 "key error" 问题。
4. 显式覆盖 SDK 默认网关到 JSON 中指定的新沙箱网关。
"""

from __future__ import annotations

import json
import threading
from pathlib import Path
from typing import Optional

_CONFIG_FILE_NAME = "alipay_sandbox.json"
_DEFAULT_GATEWAY = "https://openapi-sandbox.dl.alipaydev.com/gateway.do"


class AlipayConfigError(RuntimeError):
    """支付宝配置缺失或加载失败时抛出。"""


def _backend_dir() -> Path:
    """返回 white_back_end 目录（本文件所在目录）。"""
    return Path(__file__).resolve().parent


def _config_path() -> Path:
    """返回 alipay_sandbox.json 的绝对路径。"""
    return _backend_dir() / "config" / _CONFIG_FILE_NAME


def _load_json_config(path: Path) -> dict:
    """读取并解析 JSON 配置文件。"""
    try:
        with path.open("r", encoding="utf-8") as fh:
            data = json.load(fh)
    except FileNotFoundError as exc:
        raise AlipayConfigError(
            f"支付宝配置文件不存在：{path}。"
            f"请复制 config/alipay_sandbox.example.json 为 config/{_CONFIG_FILE_NAME} 并填入真实参数。"
        ) from exc
    except json.JSONDecodeError as exc:
        raise AlipayConfigError(
            f"支付宝配置文件 JSON 格式错误：{path}（{exc}）"
        ) from exc
    except OSError as exc:
        raise AlipayConfigError(
            f"读取支付宝配置文件失败：{path}（{exc}）"
        ) from exc

    if not isinstance(data, dict):
        raise AlipayConfigError(
            f"支付宝配置文件格式错误：{path}，根节点必须是 JSON 对象。"
        )
    return data


def _wrap_pem(raw_key: str, label: str) -> str:
    """
    把 JSON 中无头尾的 base64 字符串包装成标准 PEM 格式。
    label 例如 "RSA PRIVATE KEY" 或 "PUBLIC KEY"。
    """
    raw_key = raw_key.strip().replace("\n", "").replace(" ", "")
    body = "\n".join(raw_key[k : k + 64] for k in range(0, len(raw_key), 64))
    return f"-----BEGIN {label}-----\n{body}\n-----END {label}-----\n"


def _normalize_optional_url(value) -> Optional[str]:
    """把 JSON 中的 return_url / notify_url 规范化为 str 或 None。"""
    if value is None:
        return None
    text = str(value).strip()
    return text or None


_config_lock = threading.Lock()
_cached_config: Optional[dict] = None
_cached_client = None  # AliPay 实例


def load_alipay_config() -> dict:
    """读取并校验支付宝配置，返回 dict（不在日志中输出密钥）。"""
    global _cached_config
    if _cached_config is not None:
        return _cached_config

    with _config_lock:
        if _cached_config is not None:
            return _cached_config

        path = _config_path()
        raw = _load_json_config(path)

        cfg: dict = {
            "app_id": str(raw.get("app_id", "")).strip(),
            "gateway": str(raw.get("gateway", "")).strip() or _DEFAULT_GATEWAY,
            "app_private_key": str(raw.get("app_private_key", "")).strip(),
            "alipay_public_key": str(raw.get("alipay_public_key", "")).strip(),
            "return_url": _normalize_optional_url(raw.get("return_url")),
            "notify_url": _normalize_optional_url(raw.get("notify_url")),
        }

        # 必填字段校验
        required = ["app_id", "app_private_key", "alipay_public_key", "gateway"]
        missing = [k for k in required if not cfg.get(k)]
        if missing:
            raise AlipayConfigError(
                "支付宝配置缺失字段: " + ", ".join(missing) + f"。请检查 {path} 是否填写完整。"
            )

        # 把无头尾的 base64 字符串包装为 PEM，方便 SDK 直接使用
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
    若 JSON 中 return_url 为空，则用 default（通常是 request.host_url + 'api/payment/return'）。
    """
    cfg = load_alipay_config()
    return cfg.get("return_url") or default


def get_notify_url(default: Optional[str] = None) -> Optional[str]:
    """
    返回支付宝异步通知地址。
    若 JSON 中 notify_url 为空，则用 default（通常是 request.host_url + 'api/payment/notify'）。
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
