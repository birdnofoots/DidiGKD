#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""上传加密 secret 到 GitHub 仓库（GitHub 强制 libsodium 加密）。
用法: python3 upload_secret.py <jks文件> <storepass> <alias> <keypass>
"""
import sys, json, base64, urllib.request, nacl.public

TOKEN = open("/data/data/com.termux/files/home/gitkey").read().strip()
REPO = "birdnofoots/DidiGKD"
PROXY = "http://127.0.0.1:7890"
opener = urllib.request.build_opener(
    urllib.request.ProxyHandler({"http": PROXY, "https": PROXY}))

def api(path, data=None, method=None):
    req = urllib.request.Request(
        f"https://api.github.com{path}",
        data=json.dumps(data).encode() if data else None,
        headers={"Authorization": f"Bearer {TOKEN}",
                 "Accept": "application/vnd.github+json",
                 "Content-Type": "application/json"},
        method=method or ("POST" if data else "GET"))
    try:
        raw = opener.open(req, timeout=30).read()
        # 204 No Content(更新已有 secret)时 body 为空, 不能 json.loads
        return json.loads(raw) if raw.strip() else {"ok": True}
    except urllib.error.HTTPError as e:
        body = e.read().decode("utf-8", "replace")
        print(f"  ❌ HTTP {e.code} on {path}: {body[:300]}")
        raise

def put_secret(name, plaintext, is_file=False):
    pk = api(f"/repos/{REPO}/actions/secrets/public-key")
    # GitHub 用 libsodium sealed box(Curve25519)，不是 SecretBox(对称)
    pub = nacl.public.PublicKey(base64.b64decode(pk["key"]))
    box = nacl.public.SealedBox(pub)
    if isinstance(plaintext, str):
        plaintext = plaintext.encode("utf-8")
    enc = base64.b64encode(box.encrypt(plaintext)).decode()
    api(f"/repos/{REPO}/actions/secrets/{name}",
        {"encrypted_value": enc, "key_id": pk["key_id"]}, method="PUT")
    print(f"  ✅ {name} ({len(plaintext)} 字节)")

def main():
    jks, storepass, alias, keypass = sys.argv[1:5]
    raw = open(jks, "rb").read()
    print("上传 secrets:")
    put_secret("ANDROID_KEYSTORE_BASE64", base64.b64encode(raw).decode())
    put_secret("ANDROID_KEYSTORE_PASSWORD", storepass)
    put_secret("ANDROID_KEY_ALIAS", alias)
    put_secret("ANDROID_KEY_PASSWORD", keypass)
    print("完成。CI 里用 secrets.ANDROID_KEYSTORE_BASE64 解码即可。")

if __name__ == "__main__":
    main()
