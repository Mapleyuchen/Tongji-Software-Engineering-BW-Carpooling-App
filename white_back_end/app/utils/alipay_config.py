from __future__ import annotations

import json
import threading
from pathlib import Path
from typing import Optional

_CONFIG_FILE_NAME = "alipay_sandbox.json"
_DEFAULT_GATEWAY = "https://openapi-sandbox.dl.alipaydev.com/gateway.do"


class AlipayConfigError(RuntimeError):
    # 支付宝配置缺失或加载失败时抛出
    pass

def _backend_dir() -> Path:
    return Path(__file__).resolve().parents[2]

def _config_path() -> Path:
    return _backend_dir() / "config" / _CONFIG_FILE_NAME

# 读取并解析 JSON 配置文件
def _load_json_config(path: Path) -> dict:
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

# 把 JSON 中无头尾的 base64 字符串包装成标准 PEM 格式
def _wrap_pem(raw_key: str, label: str) -> str:
    raw_key = raw_key.strip().replace("\n", "").replace(" ", "")
    body = "\n".join(raw_key[k : k + 64] for k in range(0, len(raw_key), 64))
    return f"-----BEGIN {label}-----\n{body}\n-----END {label}-----\n"

# 把 JSON 中的 return_url / notify_url 规范化为 str 或 None
def _normalize_optional_url(value) -> Optional[str]:
    if value is None:
        return None
    text = str(value).strip()
    return text or None


_config_lock = threading.Lock()
_cached_config: Optional[dict] = None
_cached_client = None  # AliPay 实例

# 读取并校验支付宝配置，返回 dict
def load_alipay_config() -> dict:
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

# 返回 python-alipay-sdk 的 AliPay 实例（懒加载 + 单例）
def get_alipay_client():
    """
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

        # python-alipay-sdk 默认 debug=True 时网关是 https://openapi.alipaydev.com/gateway.do
        # 这里强制覆盖为 aliapi.txt 指定的新沙箱网关。
        try:
            client._gateway = cfg["gateway"]
        except Exception:  # pragma: no cover
            setattr(client, "_gateway", cfg["gateway"])

        _cached_client = client
        return client

# 返回支付宝网关 URL（用于拼接 page_pay 跳转链接）
def get_gateway() -> str:
    return load_alipay_config()["gateway"]

# 返回支付宝同步跳转地址
def get_return_url(default: Optional[str] = None) -> Optional[str]:
    cfg = load_alipay_config()
    return cfg.get("return_url") or default

# 返回支付宝异步通知地址
# 注意：localhost 地址支付宝服务器访问不到，需要通过 ngrok/cpolar 等隧道暴露。
def get_notify_url(default: Optional[str] = None) -> Optional[str]:
    cfg = load_alipay_config()
    return cfg.get("notify_url") or default

# 重置缓存（测试用）
def reset_cache_for_tests() -> None:
    global _cached_config, _cached_client
    with _config_lock:
        _cached_config = None
        _cached_client = None
