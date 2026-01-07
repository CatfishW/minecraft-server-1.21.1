import logging
import uuid
import os
import qrcode
import base64
from io import BytesIO
from typing import Dict, Optional
from fastapi import FastAPI, HTTPException, Header, BackgroundTasks, Request
from fastapi.responses import HTMLResponse
from pydantic import BaseModel
from dotenv import load_dotenv

# --- Configuration ---
load_dotenv()

SECRET_API_KEY = "novus-secure-setup-key-123"
WECHATPAY_MCHID = os.getenv("WECHATPAY_MCHID")
WECHATPAY_SERIAL_NO = os.getenv("WECHATPAY_SERIAL_NO")
WECHATPAY_APIV3_KEY = os.getenv("WECHATPAY_APIV3_KEY")
WECHATPAY_KEY_PATH = os.getenv("WECHATPAY_KEY_PATH", "./certs/apiclient_key.pem")
WECHATPAY_NOTIFY_URL = os.getenv("WECHATPAY_NOTIFY_URL", "http://mc.agaii.org:8000/webhook/wechat_notify")
PUBLIC_URL = os.getenv("PUBLIC_URL", "http://mc.agaii.org:8000")

# --- Database (Mock) ---
orders_db: Dict[str, dict] = {}

# --- FastAPI App ---
app = FastAPI(title="NovusPay Service")

# --- Logging ---
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("novus_pay")

# --- Models ---
class CreateOrderRequest(BaseModel):
    player_name: str
    amount_cny: float
    game_coins: int

class OrderResponse(BaseModel):
    order_id: str
    checkout_url: str  # URL to the web checkout page
    qr_code_url: str   # Backward compatibility for 'weixin://' link
    status: str

class StatusResponse(BaseModel):
    order_id: str
    status: str

# --- Helpers ---
def verify_internal_auth(x_api_key: str = Header(...)):
    if x_api_key != SECRET_API_KEY:
        raise HTTPException(status_code=403, detail="Invalid API Key")

def generate_qr_base64(data: str) -> str:
    qr = qrcode.QRCode(version=1, box_size=10, border=5)
    qr.add_data(data)
    qr.make(fit=True)
    img = qr.make_image(fill_color="black", back_color="white")
    buffered = BytesIO()
    img.save(buffered, format="PNG")
    return base64.b64encode(buffered.getvalue()).decode()

# --- Internal API ---

@app.post("/internal/create_order", response_model=OrderResponse)
async def create_order(req: CreateOrderRequest, req_request: Request, x_api_key: str = Header(...)):
    verify_internal_auth(x_api_key)
    logger.info(f"Headers: {req_request.headers}")
    
    order_id = str(uuid.uuid4())
    
    # Init WeChat Pay if credentials exist
    wx_pay = None
    if WECHATPAY_MCHID and WECHATPAY_SERIAL_NO and WECHATPAY_APIV3_KEY and os.path.exists(WECHATPAY_KEY_PATH):
        try:
            with open(WECHATPAY_KEY_PATH) as f:
                private_key = f.read()
            from wechatpayv3 import WeChatPay, WeChatPayType
            wx_pay = WeChatPay(
                wechatpay_type=WeChatPayType.NATIVE,
                mchid=WECHATPAY_MCHID,
                private_key=private_key,
                cert_serial_no=WECHATPAY_SERIAL_NO,
                apiv3_key=WECHATPAY_APIV3_KEY,
                cert_dir=os.path.dirname(WECHATPAY_KEY_PATH),
                logger=logger
            )
        except Exception as e:
            logger.error(f"Failed to init WeChat Pay: {e}")

    if wx_pay:
        # Real Payment
        code, message = wx_pay.pay(
            description=f"Novus Coins x{req.game_coins}",
            out_trade_no=order_id,
            amount={'total': int(req.amount_cny * 100), 'currency': 'CNY'},
            notify_url=WECHATPAY_NOTIFY_URL
        )
        if code == 200:
             import json
             res_json = json.loads(message)
             mock_wx_url = res_json.get('code_url')
        else:
             logger.error(f"WeChat Pay Error: {message}")
             raise HTTPException(status_code=500, detail="Payment Provider Error")
    else:
        # Mock Mode
        mock_wx_url = f"weixin://wxpay/bizpayurl?pr={order_id[:8]}"
    
    # Determine Host URL (heuristic or config)
    # For now, we assume the Request's base URL is reachable by user
    # Use configured Public URL
    host_url = PUBLIC_URL
    checkout_url = f"{host_url}/checkout/{order_id}"
    
    orders_db[order_id] = {
        "order_id": order_id,
        "player_name": req.player_name,
        "amount_cny": req.amount_cny,
        "game_coins": req.game_coins,
        "wx_url": mock_wx_url,
        "status": "PENDING"
    }
    
    logger.info(f"Created order {order_id} for {req.player_name}")
    
    response_data = {
        "order_id": order_id,
        "checkout_url": checkout_url,
        "qr_code_url": checkout_url, # TRICK: Send WEB LINK to old mod so it opens browser
        "status": "PENDING"
    }
    logger.info(f"Returning response: {response_data}")
    return OrderResponse(**response_data)

@app.get("/internal/check_status/{order_id}", response_model=StatusResponse)
async def check_status(order_id: str, x_api_key: str = Header(...)):
    verify_internal_auth(x_api_key)
    if order_id not in orders_db:
        raise HTTPException(status_code=404, detail="Order not found")
    return StatusResponse(order_id=order_id, status=orders_db[order_id]["status"])

@app.post("/internal/complete_order/{order_id}")
async def complete_order(order_id: str, x_api_key: str = Header(...)):
    verify_internal_auth(x_api_key)
    if order_id not in orders_db:
        raise HTTPException(status_code=404, detail="Order not found")
    orders_db[order_id]["status"] = "COMPLETED"
    return {"status": "success"}

# --- Public Web Checkout ---

@app.get("/checkout/{order_id}", response_class=HTMLResponse)
async def checkout_page(order_id: str):
    if order_id not in orders_db:
        return "<h1>Order not found</h1>"
    
    order = orders_db[order_id]
    
    if order["status"] == "COMPLETED":
        return "<h1>Payment Already Completed</h1>"
        
    qr_b64 = generate_qr_base64(order["wx_url"])
    
    html = f"""
    <!DOCTYPE html>
    <html>
    <head>
        <title>NovusPay Checkout</title>
        <style>
            body {{ font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; background-color: #f0f2f5; display: flex; justify-content: center; alignItems: center; height: 100vh; margin: 0; }}
            .card {{ background: white; padding: 2rem; border-radius: 12px; box-shadow: 0 4px 6px -1px rgba(0,0,0,0.1); text-align: center; max-width: 400px; width: 100%; }}
            .amount {{ font-size: 2rem; font-weight: bold; color: #1a1a1a; margin: 1rem 0; }}
            .player {{ color: #666; margin-bottom: 2rem; }}
            .qr-container {{ background: #fafafa; padding: 1rem; border-radius: 8px; display: inline-block; }}
            img {{ width: 200px; height: 200px; }}
            .status {{ margin-top: 1.5rem; color: #4caf50; font-weight: 500; }}
            .logo {{ font-size: 1.5rem; font-weight: bold; color: #2196f3; margin-bottom: 0.5rem; }}
        </style>
        <script>
            // Simple polling to redirect or show success
            setInterval(function() {{
                // In a real app, we'd poll a status endpoint
            }}, 3000);
        </script>
    </head>
    <body>
        <div class="card">
            <div class="logo">NovusPay</div>
            <div>Payment for Novus Coins</div>
            <div class="amount">¥{order['amount_cny']:.2f}</div>
            <div class="player">Player: {order['player_name']}</div>
            
            <div class="qr-container">
                <img src="data:image/png;base64,{qr_b64}" alt="WeChat Pay QR">
            </div>
            
            <div class="status">Scan with WeChat to Pay</div>
            <p style="font-size: 0.8rem; color: #999; margin-top: 2rem;">Order ID: {order_id}</p>
        </div>
    </body>
    </html>
    """
    return html

# --- External Mock Webhook ---

@app.post("/webhook/mock_simulate_payment")
async def mock_simulate_payment(order_id: str):
    if order_id not in orders_db:
        raise HTTPException(status_code=404, detail="Order not found")
    orders_db[order_id]["status"] = "PAID"
    return {"status": "simulated_success"}

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)
