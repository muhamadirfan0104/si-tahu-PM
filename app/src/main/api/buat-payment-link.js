function setCors(res) {
  res.setHeader("Access-Control-Allow-Origin", "*");
  res.setHeader("Access-Control-Allow-Methods", "POST, OPTIONS");
  res.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization");
}

function midtransBaseUrl() {
  return process.env.MIDTRANS_IS_PRODUCTION === "true"
    ? "https://api.midtrans.com"
    : "https://api.sandbox.midtrans.com";
}

function midtransAuthHeader() {
  const serverKey = process.env.MIDTRANS_SERVER_KEY;

  if (!serverKey) {
    throw new Error("MIDTRANS_SERVER_KEY belum diatur di Vercel.");
  }

  return "Basic " + Buffer.from(serverKey + ":").toString("base64");
}

export default async function handler(req, res) {
  setCors(res);

  if (req.method === "OPTIONS") {
    return res.status(200).end();
  }

  if (req.method !== "POST") {
    return res.status(405).json({ message: "Method tidak diizinkan. Gunakan POST." });
  }

  try {
    const total = Number(req.body?.total || 0);

    if (!Number.isInteger(total) || total <= 0) {
      return res.status(400).json({ message: "Total transaksi tidak valid." });
    }

    const orderId = `SITAHU-${Date.now()}`;

    const body = {
      transaction_details: {
        order_id: orderId,
        gross_amount: total,
      },
      usage_limit: 1,
    };

    const response = await fetch(`${midtransBaseUrl()}/v1/payment-links`, {
      method: "POST",
      headers: {
        Accept: "application/json",
        "Content-Type": "application/json",
        Authorization: midtransAuthHeader(),
      },
      body: JSON.stringify(body),
    });

    const json = await response.json();

    if (!response.ok || json.error_messages || Number(json.status_code || 200) >= 400) {
      return res.status(400).json({
        message:
          json.status_message ||
          json.error_messages?.join(", ") ||
          "Gagal membuat Payment Link Midtrans.",
        midtrans: json,
      });
    }

    const paymentUrl =
      json.payment_url || json.payment_link_url || json.redirect_url || json.url || "";

    if (!paymentUrl) {
      return res.status(400).json({
        message: "Payment URL Midtrans kosong.",
        midtrans: json,
      });
    }

    return res.status(200).json({
      orderId,
      total,
      paymentUrl,
      raw: json,
    });
  } catch (error) {
    return res.status(500).json({
      message: error.message || "Terjadi kesalahan server.",
    });
  }
}
