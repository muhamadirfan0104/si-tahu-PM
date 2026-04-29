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
    const orderId = String(req.body?.orderId || "");

    if (!orderId) {
      return res.status(400).json({ message: "Order ID kosong." });
    }

    const response = await fetch(
      `${midtransBaseUrl()}/v2/${encodeURIComponent(orderId)}/status`,
      {
        method: "GET",
        headers: {
          Accept: "application/json",
          "Content-Type": "application/json",
          Authorization: midtransAuthHeader(),
        },
      }
    );

    const json = await response.json();

    if (!response.ok) {
      const statusCode = String(json.status_code || response.status);
      const message = String(json.status_message || "").toLowerCase();
      const belumAdaTransaksi = statusCode === "404" || message.includes("doesn't exist");

      if (belumAdaTransaksi) {
        return res.status(200).json({
          orderId,
          transactionId: "",
          transactionStatus: "pending",
          fraudStatus: "",
          grossAmount: "",
          paymentType: "payment_link",
          settlementTime: "",
          raw: json,
        });
      }

      return res.status(response.status).json({
        message: json.status_message || "Gagal cek status Midtrans.",
        midtrans: json,
      });
    }

    return res.status(200).json({
      orderId,
      transactionId: json.transaction_id || "",
      transactionStatus: json.transaction_status || "pending",
      fraudStatus: json.fraud_status || "",
      grossAmount: json.gross_amount || "",
      paymentType: json.payment_type || "",
      settlementTime: json.settlement_time || "",
      raw: json,
    });
  } catch (error) {
    return res.status(500).json({
      message: error.message || "Terjadi kesalahan server.",
    });
  }
}
