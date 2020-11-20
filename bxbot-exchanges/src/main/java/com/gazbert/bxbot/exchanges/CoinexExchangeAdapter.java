package com.gazbert.bxbot.exchanges;

import java.math.BigDecimal;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.gazbert.bxbot.exchange.api.AuthenticationConfig;
import com.gazbert.bxbot.exchange.api.ExchangeAdapter;
import com.gazbert.bxbot.exchange.api.ExchangeConfig;
import com.gazbert.bxbot.exchange.api.OtherConfig;
import com.gazbert.bxbot.exchanges.trading.api.impl.BalanceInfoImpl;
import com.gazbert.bxbot.exchanges.trading.api.impl.MarketOrderBookImpl;
import com.gazbert.bxbot.exchanges.trading.api.impl.MarketOrderImpl;
import com.gazbert.bxbot.trading.api.BalanceInfo;
import com.gazbert.bxbot.trading.api.ExchangeNetworkException;
import com.gazbert.bxbot.trading.api.MarketOrder;
import com.gazbert.bxbot.trading.api.MarketOrderBook;
import com.gazbert.bxbot.trading.api.OpenOrder;
import com.gazbert.bxbot.trading.api.OrderType;
import com.gazbert.bxbot.trading.api.TradingApiException;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;

/**
 * CoinEX Echange
 * 
 * 
 * ## Acquire access_id and secret_key
 * 
 * Sign in to CoinEx before invoking API and get Acquire access_id/secret_key in
 * Account > API.
 * 
 * Note: access_id/secret_key is equivalent to your account/password. For your
 * asset security, please keep it safe and change it regularly. Once lost,
 * please sign in to CoinEx for reset.
 * 
 * @author maso
 * @author hadi
 *
 */
public class CoinexExchangeAdapter extends AbstractExchangeAdapter implements ExchangeAdapter {

	// --------------------------------------------------------------------------
	// Error messages
	// --------------------------------------------------------------------------
	private static final String UNEXPECTED_IO_ERROR_MSG = "Failed to connect to Exchange due to unexpected IO error.";
	private static final String UNEXPECTED_ERROR_MSG = "Unexpected error has occurred in CoinEx Exchange Adapter. ";
	// --------------------------------------------------------------------------
	// Error codes
	// --------------------------------------------------------------------------
	// Success
	public static final int SUCCESS = 0;
	// Error
	public static final int ERROR_UNKOWN = 1;
	// Parameter error
	public static final int ERROR_PARAMETER = 2;
	// Internal error
	public static final int ERROR_INTERNAL = 3;
	// Signature error
	public static final int ERROR_SIGNATURE = 25;
	// Service unavailable
	public static final int ERROR_SERVICE_UNAVAILABLE = 35;
	// Service timeout
	public static final int ERROR_TIMEOUT = 36;
	// Main and sub accounts unpaired
	public static final int ERROR_MAIN_SUB_ACCOUNTS = 40;
	// Transfer to sub account rejected
	public static final int ERROR_TRANSFER_SUB_ACCOUNT_REJECTED = 49;
	// Insufficient balance
	public static final int ERROR_INSUFFICIENT_BALANCE = 107;
	// forbid trading
	public static final int ERROR_FORBID_TRADING = 115;
	// tonce check error, correct tonce should be within one minute of the current
	// time
	public static final int ERROR_TONCE_CHECK = 227;
	// Order number does not exist
	public static final int ERROR_ORDER_NUMBER_NOT_EXIST = 600;
	// Other user's order
	public static final int ERROR_BAD_USER_ORDER = 601;
	// Below min. buy/sell limit
	public static final int ERROR_BELOW_MIN_LIMIT = 602;
	// Order price and the latest price deviation is too large
	public static final int ERROR_PRICE_DEVIATION_IS_TOO_LARGE = 606;
	// Merge depth error
	public static final int ERROR_MERGE_DEPTH = 651;

	// --------------------------------------------------------------------------
	// Configuration keys
	// --------------------------------------------------------------------------
	public static final String CONFIG_ACCESS_ID = "access-id";
	public static final String CONFIG_SECRET_KEY = "secret-key";
	public static final String CONFIG_BASE_URL = "base-url";
	public static final String CONFIG_MARKET_DEPTH_MERGE = "market-depth-merge";

	// --------------------------------------------------------------------------
	// Request values
	// --------------------------------------------------------------------------
	public static final String BASE_URL = "https://api.coinex.com/v1";
	/**
	 * All requests based on the Https protocol should set the request header
	 * information Content-Type as:'application/json’. All input and output data are
	 * in JSON format.
	 */
	public static final String CONTENT_TYPE = "application/json";
	public static final String ACCEPT_TYPE = "*/*";

	/**
	 * Request header information must be declared: User-Agent:
	 */
	public static final String User_Agent = "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/39.0.2171.71 Safari/537.36";

	private static final char HEX_DIGITS[] = { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D',
			'E', 'F' };
	// --------------------------------------------------------------------------
	// Util
	// --------------------------------------------------------------------------
	private static final Logger logger = LogManager.getLogger(CoinexExchangeAdapter.class);

	// --------------------------------------------------------------------------
	// Authentication
	// --------------------------------------------------------------------------
	/**
	 * Signature is required for Account API and trading API related interfaces. The
	 * signature data is placed in the authorization header of the HTTP header and
	 * authorization is the signature result string. No signature is required for
	 * market API related interfaces.
	 */
//	private String signature;

	/**
	 * access_id: To mark identity of API invoker
	 */
	private String accessId;
	/**
	 * secret_key: Key to sign the request parameters
	 */
	private String secretKey;

	private double marketDepthMerge = 0.1;

	// --------------------------------------------------------------------------
	// HTTP request
	// --------------------------------------------------------------------------
	private String baseUrl;
	private Gson gson;

	@Override
	public String getImplName() {
		return "CoinEx Quantitative Trading API V1";
	}

	@Override
	public void init(ExchangeConfig config) {
		logger.info(() -> "Start to initialise CoinexEx ExchangeConfig: " + config);
		setAuthenticationConfig(config);
		setEndpointConfig(config);
		setNetworkConfig(config);
		initGson();
	}

	@Override
	public MarketOrderBook getMarketOrders(String marketId) throws ExchangeNetworkException, TradingApiException {
		try {
			Map<String, Object> params = createRequestParamMap();
			params.put("market", marketId);
			params.put("limit", 50);
			params.put("merge", marketDepthMerge);
			ExchangeHttpResponse response = doRequest("/market/depth", params, "GET");
			logger.debug(() -> "The market depth: " + response);

			final MarketDepthMessage mdm = gson.fromJson(response.getPayload(), MarketDepthMessage.class);

			List<MarketOrder> sellOrders = new ArrayList<MarketOrder>();
			List<MarketOrder> buyOrders = new ArrayList<MarketOrder>();

			mdm.marketDepth.asks.forEach(item -> {
				BigDecimal price = item.get(0);
				BigDecimal amount = item.get(1);
				BigDecimal total = price.multiply(amount);
				sellOrders.add(new MarketOrderImpl(OrderType.SELL, price, amount, total));
			});

			mdm.marketDepth.bids.forEach(item -> {
				BigDecimal price = item.get(0);
				BigDecimal amount = item.get(1);
				BigDecimal total = price.multiply(amount);
				buyOrders.add(new MarketOrderImpl(OrderType.SELL, price, amount, total));
			});

			MarketOrderBookImpl mob = new MarketOrderBookImpl(marketId, sellOrders, buyOrders);
			return mob;
		} catch (ExchangeNetworkException | TradingApiException e) {
			throw e;
		} catch (Exception e) {
			logger.error(UNEXPECTED_ERROR_MSG, e);
			throw new TradingApiException(UNEXPECTED_ERROR_MSG, e);
		}
	}

	@Override
	public List<OpenOrder> getYourOpenOrders(String marketId) throws ExchangeNetworkException, TradingApiException {
		try {
			Map<String, Object> params = createRequestParamMap();
			params.put("market", marketId);
			params.put("page", 1);
			params.put("limit", 100);
			params.put("account_id", 0);
			ExchangeHttpResponse response = doRequest("/order/pending", params, "GET");
			logger.debug(() -> "The market latest transaction: " + response);

			final TradingUnexecutedOrdersMessage tuo = gson.fromJson(response.getPayload(),
					TradingUnexecutedOrdersMessage.class);

			List<OpenOrder> list = new ArrayList<OpenOrder>();
			list.addAll(tuo.pagenatedList.orders);
			return list;
		} catch (ExchangeNetworkException | TradingApiException e) {
			throw e;
		} catch (Exception e) {
			logger.error(UNEXPECTED_ERROR_MSG, e);
			throw new TradingApiException(UNEXPECTED_ERROR_MSG, e);
		}
	}

	@Override
	public String createOrder(String marketId, OrderType orderType, BigDecimal quantity, BigDecimal price)
			throws ExchangeNetworkException, TradingApiException {
		try {
			Map<String, Object> params = createRequestParamMap();
			params.put("market", marketId);
			switch (orderType) {
			case BUY:
				params.put("type", "buy");
				break;
			case SELL:
				params.put("type", "sell");
				break;
			}
			params.put("amount", quantity.toString());
			params.put("price", price.toString());
			params.put("account_id", 0);
			ExchangeHttpResponse response = doRequest("/order/limit", params, "POST");
			logger.debug(() -> "To create an order: " + response);

			final TradingLimitOrdersMessage tlo = gson.fromJson(response.getPayload(), TradingLimitOrdersMessage.class);

			if (!tlo.isSuccess()) {
				throw tlo.toException();
			}

			return String.valueOf(tlo.order.id);
		} catch (ExchangeNetworkException | TradingApiException e) {
			throw e;
		} catch (Exception e) {
			logger.error(UNEXPECTED_ERROR_MSG, e);
			throw new TradingApiException(UNEXPECTED_ERROR_MSG, e);
		}
	}

	@Override
	public boolean cancelOrder(String orderId, String marketId) throws ExchangeNetworkException, TradingApiException {
		try {
			Map<String, Object> params = createRequestParamMap();
			params.put("market", marketId);
			params.put("id", orderId);
			params.put("account_id", 0);
			ExchangeHttpResponse response = doRequest("/order/pending", params, "DELETE");
			logger.debug(() -> "Cancel Order: " + response);

			final TradingLimitOrdersMessage tlo = gson.fromJson(response.getPayload(), TradingLimitOrdersMessage.class);

			if (!tlo.isSuccess()) {
				return false;
			}

			return true;
		} catch (ExchangeNetworkException | TradingApiException e) {
			throw e;
		} catch (Exception e) {
			logger.error(UNEXPECTED_ERROR_MSG, e);
			throw new TradingApiException(UNEXPECTED_ERROR_MSG, e);
		}
	}

	@Override
	public BigDecimal getLatestMarketPrice(String marketId) throws ExchangeNetworkException, TradingApiException {
		try {
			Map<String, Object> params = createRequestParamMap();
			params.put("market", marketId);
			params.put("limit", 1);
			ExchangeHttpResponse response = doRequest("/market/deals", params, "GET");
			logger.debug(() -> "The market latest transaction: " + response);

			final MarketLatestTransactionsMessage mltm = gson.fromJson(response.getPayload(),
					MarketLatestTransactionsMessage.class);
			return mltm.list.get(0).price;
		} catch (ExchangeNetworkException | TradingApiException e) {
			throw e;
		} catch (Exception e) {
			logger.error(UNEXPECTED_ERROR_MSG, e);
			throw new TradingApiException(UNEXPECTED_ERROR_MSG, e);
		}
	}

	public List<String> getMarketList() throws ExchangeNetworkException, TradingApiException {
		try {
			ExchangeHttpResponse response = doRequest("/market/list", null, "GET");
			logger.debug(() -> "The market list: " + response);

			final MarketListMessage marketsList = gson.fromJson(response.getPayload(), MarketListMessage.class);
			return marketsList.list;
		} catch (ExchangeNetworkException | TradingApiException e) {
			throw e;
		} catch (Exception e) {
			logger.error(UNEXPECTED_ERROR_MSG, e);
			throw new TradingApiException(UNEXPECTED_ERROR_MSG, e);
		}
	}

	@Override
	public BalanceInfo getBalanceInfo() throws ExchangeNetworkException, TradingApiException {
		try {
			ExchangeHttpResponse response = doRequest("/balance/info", null, "GET");
			logger.debug(() -> "Balance Info response: " + response);

			final CoinexBalanceMessage balances = gson.fromJson(response.getPayload(), CoinexBalanceMessage.class);

			final Map<String, BigDecimal> balancesAvailable = new HashMap<>();
			final Map<String, BigDecimal> balancesOnOrder = new HashMap<>();
			balances.wallets.forEach((key, wallet) -> {
				balancesAvailable.put(key, wallet.available);
				balancesAvailable.put(key, wallet.frozen);
			});

			return new BalanceInfoImpl(balancesAvailable, balancesOnOrder);
		} catch (ExchangeNetworkException | TradingApiException e) {
			throw e;
		} catch (Exception e) {
			logger.error(UNEXPECTED_ERROR_MSG, e);
			throw new TradingApiException(UNEXPECTED_ERROR_MSG, e);
		}
	}

	/*
	 * buy fee is as equal as sell
	 */
	@Override
	public BigDecimal getPercentageOfBuyOrderTakenForExchangeFee(String marketId)
			throws TradingApiException, ExchangeNetworkException {
		return getPercentageOfSellOrderTakenForExchangeFee(marketId);
	}

	/*
	 * Get markets info and extract market fee rate
	 */
	@Override
	public BigDecimal getPercentageOfSellOrderTakenForExchangeFee(String marketId)
			throws TradingApiException, ExchangeNetworkException {
		try {
			Map<String, Object> params = createRequestParamMap();
			params.put("market", marketId);
			ExchangeHttpResponse response = doRequest("/market/info", params, "GET");
			logger.debug(() -> "Market Info response: " + response);

			final MarketInfoMessage marketInfoMessage = gson.fromJson(response.getPayload(), MarketInfoMessage.class);

			return marketInfoMessage.markets.get(marketId).makerFeeRate;
		} catch (ExchangeNetworkException | TradingApiException e) {
			throw e;
		} catch (Exception e) {
			logger.error(UNEXPECTED_ERROR_MSG, e);
			throw new TradingApiException(UNEXPECTED_ERROR_MSG, e);
		}
	}

	// --------------------------------------------------------------------------
	// Config methods
	// --------------------------------------------------------------------------
	private void setAuthenticationConfig(ExchangeConfig config) {
		final AuthenticationConfig authenticationConfig = getAuthenticationConfig(config);
		accessId = getAuthenticationConfigItem(authenticationConfig, CONFIG_ACCESS_ID);
		secretKey = getAuthenticationConfigItem(authenticationConfig, CONFIG_SECRET_KEY);
	}

	private void setEndpointConfig(ExchangeConfig config) {
		final OtherConfig otherConfig = getOtherConfig(config);
		// Base url
		try {
			baseUrl = getOtherConfigItem(otherConfig, CONFIG_BASE_URL);
		} catch (IllegalArgumentException ex) {
			baseUrl = BASE_URL;
		}

		String mergeVal = getOtherConfigItem(otherConfig, CONFIG_MARKET_DEPTH_MERGE);
		marketDepthMerge = Double.parseDouble(mergeVal);
	}

	// --------------------------------------------------------------------------
	// Util methods
	// --------------------------------------------------------------------------

	private void initGson() {
		final GsonBuilder gsonBuilder = new GsonBuilder();
//		gsonBuilder.registerTypeAdapter(Date.class, new BitstampDateDeserializer());
		gson = gsonBuilder.create();
	}

	/*
	 * Hack for unit-testing map params passed to transport layer.
	 */
	private Map<String, Object> createRequestParamMap() {
		return new HashMap<>();
	}

	/*
	 * Hack for unit-testing header params passed to transport layer.
	 */
	private Map<String, String> createHeaderParamMap() {
		return new HashMap<>();
	}

	private ExchangeHttpResponse doRequest(String api, Map<String, Object> paramMap, String method)
			throws TradingApiException, ExchangeNetworkException {
		if (paramMap == null) {
			paramMap = createRequestParamMap();
		}
		paramMap.put("access_id", this.accessId);

		/*
		 * tonce Integer Yes Tonce is a timestamp with a positive Interger that
		 * represents the number of milliseconds from Unix epoch to the current time.
		 * Error between tonce and server time can not exceed plus or minus 60s
		 */
		paramMap.put("tonce", System.currentTimeMillis());

		Map<String, String> requestHeaders = createHeaderParamMap();
		requestHeaders.put("Content-Type", CONTENT_TYPE);
		requestHeaders.put("Accept", ACCEPT_TYPE);
		/*
		 * authorization:"xxxx"(32-digit capital letters, see generating method in <API
		 * invocation instruction>)
		 */
		requestHeaders.put("authorization", buildMysignV1(paramMap, secretKey));
		requestHeaders.put("User-Agent",
				"Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/39.0.2171.71 Safari/537.36");

		String postData = null;

		if ("POST".equalsIgnoreCase(method)) {
			postData = gson.toJson(paramMap);
		} else {
			api = api + "?" + createLinkString(paramMap);
		}
		try {
			final URL url = new URL(baseUrl + api);
			return sendNetworkRequest(url, method, postData, requestHeaders);
		} catch (MalformedURLException e) {
			final String errorMsg = UNEXPECTED_IO_ERROR_MSG;
			logger.error(errorMsg, e);
			throw new TradingApiException(errorMsg, e);
		}
	}

	public static String buildMysignV1(Map<String, Object> sArray, String secretKey) {
		String mysign = "";
		try {
			String prestr = createLinkString(sArray);
			prestr = prestr + "&secret_key=" + secretKey;
			mysign = getMD5String(prestr);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return mysign;
	}

	public static String getMD5String(String str) {
		try {
			if (str == null || str.trim().length() == 0) {
				return "";
			}
			byte[] bytes = str.getBytes();
			MessageDigest messageDigest = MessageDigest.getInstance("MD5");
			messageDigest.update(bytes);
			bytes = messageDigest.digest();
			StringBuilder sb = new StringBuilder();
			for (int i = 0; i < bytes.length; i++) {
				sb.append(HEX_DIGITS[(bytes[i] & 0xf0) >> 4] + "" + HEX_DIGITS[bytes[i] & 0xf]);
			}
			return sb.toString();
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		}
		return "";
	}

	public static boolean isEmpty(String str) {
		if (str == null)
			return true;
		String tempStr = str.trim();
		if (tempStr.length() == 0)
			return true;
		if (tempStr.equals("null"))
			return true;
		return false;
	}

	public static String createLinkString(Map<String, Object> params) {
		List<String> keys = new ArrayList<String>(params.keySet());
		Collections.sort(keys);
		String prestr = "";
		for (int i = 0; i < keys.size(); i++) {
			String key = keys.get(i);
			String value = params.get(key).toString();
			if (i == keys.size() - 1) {
				prestr = prestr + key + "=" + value;
			} else {
				prestr = prestr + key + "=" + value + "&";
			}
		}
		return prestr;
	}

	// --------------------------------------------------------------------------
	// Util methods
	// --------------------------------------------------------------------------
	/**
	 * A coinex wallet
	 * 
	 * Balance in a wallet my be available or frozen.
	 * 
	 * @author maso
	 *
	 */
	class CoinexWallet {
		@SerializedName("available")
		BigDecimal available;

		@SerializedName("frozen")
		BigDecimal frozen;
	}

	class CoinexTransaction {
		Long id;
		String type;
		BigDecimal price;
		BigDecimal amount;
		Long date;
	}

	class CoinexMarket {
		/**
		 * market name
		 */
		@SerializedName("name")
		String name;

		/**
		 * taker fee
		 */
		@SerializedName("taker_fee_rate")
		BigDecimal takerFeeRate;

		/**
		 * maker fee
		 */
		@SerializedName("maker_fee_rate")
		BigDecimal makerFeeRate;

		/**
		 * Min. amount of transaction
		 */
		@SerializedName("min_amount")
		BigDecimal minAmount;

		/**
		 * trading coin type
		 */
		@SerializedName("trading_name")
		String tradingName;

		/**
		 * decimal of tradiing coin
		 */
		@SerializedName("trading_decimal")
		BigDecimal tradingDecimal;

		/**
		 * pricing coin type
		 */
		@SerializedName("pricing_name")
		String pricingName;

		/**
		 * decimal of pricing coin
		 */
		@SerializedName("pricing_decimal")
		BigDecimal pricingDecimal;

	}

	class CoinexOrder implements OpenOrder {

//		id	Interger	Order No.
		@SerializedName("id")
		long id;
//		avg_price	String	average price

//		create_time	Interger	time when placing order
		@SerializedName("create_time")
		long createTime;

//		finished_time	Interger	complete time
		@SerializedName("finished_time")
		long finishedTime;

//		maker_fee_rate	String	maker fee
		@SerializedName("maker_fee_rate")
		BigDecimal makerFeeRate;

//		market	String	See <API invocation description·market>
		@SerializedName("market")
		String marketId;

//		order_type	String	limit:limit order; market:market order;
		@SerializedName("order_type")
		String orderType;

//		amount	String	order count
		@SerializedName("amount")
		BigDecimal amount;

//		price	String	order price
		@SerializedName("price")
		BigDecimal price;

//		deal_amount	String	count
		@SerializedName("deal_amount")
		BigDecimal dealAmount;

//		deal_fee	String	transaction fee
		@SerializedName("deal_fee")
		BigDecimal dealFee;

//		deal_money	String	executed value
		@SerializedName("deal_money")
		BigDecimal dealMoney;

//		status	String	not_deal: unexecuted;
//		part_deal: partly executed;
//		done: executed;
//		To guarantee server performance, all cancelled unexecuted orders will be deleted.
		@SerializedName("status")
		String status;

//		taker_fee_rate	String	taker fee
		@SerializedName("taker_fee_rate")
		BigDecimal takerFeeRate;

//		type	String	sell: sell order; buy: buy order;
		@SerializedName("type")
		String type;

//		client_id	String	order client id
		@SerializedName("client_id")
		String clientId;

		@Override
		public String getId() {
			return String.valueOf(id);
		}

		@Override
		public Date getCreationDate() {
			return new Date(createTime);
		}

		@Override
		public String getMarketId() {
			return marketId;
		}

		@Override
		public OrderType getType() {
			if ("buy".equalsIgnoreCase(this.type)) {
				return OrderType.BUY;
			}
			return OrderType.SELL;
		}

		@Override
		public BigDecimal getPrice() {
			return price;
		}

		@Override
		public BigDecimal getQuantity() {
			return amount.subtract(dealAmount);
		}

		@Override
		public BigDecimal getOriginalQuantity() {
			return amount;
		}

		@Override
		public BigDecimal getTotal() {
			return amount.multiply(price);
		}
	}

	class CoinexUnexecutedOrderPaginatedList {
		int count; // # current page rows
		int curr_page; // # current page
		boolean has_next; // # Is there a next page
		@SerializedName("data")
		List<CoinexOrder> orders;
	}

	class CoinexMessage {
		@SerializedName("code")
		int code;

		@SerializedName("message")
		String message;

		public boolean isSuccess() {
			return code == 0;
		}

		public TradingApiException toException() {
			return new TradingApiException(code + ": " + message);
		}
	}

	class CoinexBalanceMessage extends CoinexMessage {
		@SerializedName("data")
		Map<String, CoinexWallet> wallets;
	}

	class MarketListMessage extends CoinexMessage {
		@SerializedName("data")
		List<String> list;
	}

	class MarketLatestTransactionsMessage extends CoinexMessage {
		@SerializedName("data")
		List<CoinexTransaction> list;
	}

	class MarketInfoMessage extends CoinexMessage {
		@SerializedName("data")
		Map<String, CoinexMarket> markets;
	}

	class TradingUnexecutedOrdersMessage extends CoinexMessage {
		@SerializedName("data")
		CoinexUnexecutedOrderPaginatedList pagenatedList;
	}

	class TradingLimitOrdersMessage extends CoinexMessage {
		@SerializedName("data")
		CoinexOrder order;
	}

	class CoinexMarketDepth {
		BigDecimal last;
		long time;
		List<List<BigDecimal>> asks;
		List<List<BigDecimal>> bids;
	}

	class MarketDepthMessage extends CoinexMessage {
		@SerializedName("data")
		CoinexMarketDepth marketDepth;
	}
}
