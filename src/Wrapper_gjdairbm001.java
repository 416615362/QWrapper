import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.net.SocketFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import org.apache.commons.httpclient.ConnectTimeoutException;
import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.cookie.CookiePolicy;
import org.apache.commons.httpclient.methods.InputStreamRequestEntity;
import org.apache.commons.httpclient.methods.RequestEntity;
import org.apache.commons.httpclient.params.HttpConnectionParams;
import org.apache.commons.httpclient.protocol.Protocol;
import org.apache.commons.httpclient.protocol.SecureProtocolSocketFactory;
import org.apache.commons.lang.StringUtils;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.qunar.qfwrapper.bean.booking.BookingInfo;
import com.qunar.qfwrapper.bean.booking.BookingResult;
import com.qunar.qfwrapper.bean.search.FlightDetail;
import com.qunar.qfwrapper.bean.search.FlightSearchParam;
import com.qunar.qfwrapper.bean.search.FlightSegement;
import com.qunar.qfwrapper.bean.search.OneWayFlightInfo;
import com.qunar.qfwrapper.bean.search.ProcessResultInfo;
import com.qunar.qfwrapper.constants.Constants;
import com.qunar.qfwrapper.interfaces.QunarCrawler;
import com.qunar.qfwrapper.util.QFGetMethod;
import com.qunar.qfwrapper.util.QFHttpClient;
import com.qunar.qfwrapper.util.QFPostMethod;


/**
 * 英伦航空抓取
 * @author Administrator
 *
 */
public class Wrapper_gjdairbm001 implements QunarCrawler{

	public static Map<String,String> cookieMap = new HashMap<String,String>();
	public static Map<String,String> airportMap = new HashMap<String,String>();
	// 单程航班
	public static void main(String[] args) {
		FlightSearchParam searchParam = new FlightSearchParam();
		//ABZ EBJ 2014-07-14
		//BRU EMA 2014-07-17
		//BRE TLS 2014-7-30
		searchParam.setDep("ABZ");
		searchParam.setArr("EBJ");
		searchParam.setDepDate("2014-07-14");
		searchParam.setTimeOut("60000");
		searchParam.setWrapperid("gjdairbm001");
		searchParam.setToken("");
		// 获取请求返回的html
		String html = new  Wrapper_gjdairbm001().getHtml(searchParam);
		ProcessResultInfo result = new ProcessResultInfo();
		// 拼装返回的结果
		result = new  Wrapper_gjdairbm001().process(html,searchParam);
		System.out.println(com.alibaba.fastjson.JSON.toJSONString(result,
				SerializerFeature.DisableCircularReferenceDetect));
		if(result.isRet() && result.getStatus().equals(Constants.SUCCESS))
		{
			List<OneWayFlightInfo> flightList = (List<OneWayFlightInfo>) result.getData();
			for (OneWayFlightInfo in : flightList){
				System.out.println("************" + in.getInfo().toString());
				System.out.println("++++++++++++" + in.getDetail().toString());
			}
		}
		else
		{
			System.out.println(result.getStatus());
		}
	}
	
	@SuppressWarnings("deprecation")
	public String getHtml(FlightSearchParam param) {
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
		Calendar cal = Calendar.getInstance();
		QFGetMethod get = null;
		QFPostMethod post = null;
		try {	
			QFHttpClient httpClient = new QFHttpClient(param, false);
			//对于需要cookie的网站，请自己处理cookie（必须）
			httpClient.getParams().setCookiePolicy(CookiePolicy.BROWSER_COMPATIBILITY);
			// 主页html
			String indexUrl = String.format("http://www.bmiregional.com/en");
			get = new QFGetMethod(indexUrl);
			httpClient.executeMethod(get);
			String indexHtml = get.getResponseBodyAsString();
			String airportSettings = StringUtils.substringBetween(indexHtml, "var airportSettings =", ";");
			// 航空信息转成数组
			JSONArray airportAjson = JSON.parseArray(airportSettings);
			for(int i=0;i<airportAjson.size();i++){
				JSONObject jsonObject = airportAjson.getJSONObject(i);
				airportMap.put(String.valueOf(jsonObject.get("Code")), jsonObject.toString());
			}
			String airportStr = airportMap.get(param.getDep());
			// 货币
			String currency = "";
			if(airportStr != null && !"".equals(airportStr)){
				currency = airportStr.substring(airportStr.indexOf("[")+2, airportStr.indexOf("]")-1);
			}
			// 计算起飞时间相邻一天
			Date depDate = sdf.parse(param.getDepDate());
			cal.setTime(depDate);
			cal.add(Calendar.DATE, 1);
			String depTime = sdf.format(cal.getTime());
			// 请求地址
			String getUrl = String.format("http://www.bmiregional.com/en/Flight/Search?interline=false&fromCityCode=%s&toCityCode=%s&departureDateString=%s&returnDateString=%s&adults=1&children=0&infants=0&roundTrip=false&useFlexDates=false&allInclusive=undefined&promocode=&fareTypes=undefined&currency=%s",
					param.getDep(),param.getArr(),param.getDepDate(),depTime,currency);
			get = new QFGetMethod(getUrl);
			get.setFollowRedirects(false);
			get.getParams().setContentCharset("utf-8");
			httpClient.executeMethod(get);
			if(get.getStatusCode() == HttpStatus.SC_MOVED_TEMPORARILY || get.getStatusCode() == HttpStatus.SC_MOVED_PERMANENTLY){
				Header location = get.getResponseHeader("Location");
				String url = "";
				if(location !=null){
					url = location.getValue();
					if(!url.startsWith("http")){
						url = get.getURI().getScheme() + "://" + get.getURI().getHost() + (get.getURI().getPort()==-1?"":(":"+get.getURI().getPort())) + url;
					}
				}else{
					return "";
				}
				// 处理证书问题
				Protocol.registerProtocol("https", new Protocol("https",
						new MySecureProtocolSocketFactory(), 443));
				// 第二次请求
				String cookie = StringUtils.join(httpClient.getState().getCookies(),"; ");
				cookieMap.put("cookie", cookie);
				post = new QFPostMethod("https://www.bmiregional.com/Webservices/Availability.asmx/GetAvailability");
				// 计算startDate时间
				cal.clear();
				cal.setTime(depDate);
				cal.add(Calendar.DATE, -15);
				String startDateTime = sdf.format(cal.getTime());
				// 计算endDate时间
				cal.clear();
				cal.setTime(sdf.parse(startDateTime));
				cal.add(Calendar.DATE, 30);
				String endDateTime = sdf.format(cal.getTime());
				// json参数
				String jsonbody = "{\"interline\":false,\"fromCityCode\":\""+param.getDep()+"\",\"toCityCode\":\""+param.getArr()+"\",\"departureDateString\":\""+param.getDepDate()+"\",\"returnDateString\":\""+depTime+"\",\"startDateStringOutbound\":\""+startDateTime+"\",\"endDateStringOutbound\":\""+endDateTime+"\",\"startDateStringInbound\":\"\",\"endDateStringInbound\":\"\",\"adults\":1,\"children\":0,\"infants\":0,\"roundTrip\":false,\"useFlexDates\":false,\"isOutbound\":true,\"filterMethod\":\"100\",\"promocode\":\"\",\"currency\":\""+currency+"\"}";
				// 把Soap请求数据添加到PostMethod中
				byte[] b = jsonbody.getBytes("utf-8");
				InputStream is = new ByteArrayInputStream(b, 0, b.length);
				RequestEntity re = new InputStreamRequestEntity(is, b.length,"application/json; charset=utf-8");
				post.setRequestEntity(re);
				httpClient.getState().clearCookies();
				post.addRequestHeader("Cookie",cookie+"; defaultLanguage=en;");
				httpClient.executeMethod(post);
				return post.getResponseBodyAsString();
			}
		} catch (Exception e) {			
			e.printStackTrace();
		} finally{
			if(null != get){
				get.releaseConnection();
			}
			if (null != post){
				post.releaseConnection();
			}
		}
		return "Exception";
	}

	public ProcessResultInfo process(String html, FlightSearchParam param) {
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
		/* ProcessResultInfo中，
		 * ret为true时，status可以为：SUCCESS(抓取到机票价格)|NO_RESULT(无结果，没有可卖的机票)
		 * ret为false时，status可以为:CONNECTION_FAIL|INVALID_DATE|INVALID_AIRLINE|PARSING_FAIL|PARAM_ERROR
		 */
		ProcessResultInfo result = new ProcessResultInfo();
		if ("Exception".equals(html)) {	
			result.setRet(false);
			result.setStatus(Constants.CONNECTION_FAIL);
			return result;
		}	
		try {
			// 获取对应的值
			String jsonStr = StringUtils.substringBetween(html, "Legs\":", ",\"FaresTypes\"");
			String jsonPrice = StringUtils.substringBetween(html, "FaresTypes\":", ",\"LowestFareSummary\"");
			// 判断是否为空
			if (StringUtils.isEmpty(jsonStr) || StringUtils.isEmpty(jsonPrice)) {
				result.setRet(false);
				result.setStatus(Constants.NO_RESULT);
				return result;
			}
			// 航班数组
			JSONArray ajson = JSON.parseArray(jsonStr);
			// 金额数组
			JSONArray ajsonPrice = JSON.parseArray(jsonPrice); // 价格json
			// 单程航班信息
			List<OneWayFlightInfo> flightList = new ArrayList<OneWayFlightInfo>();
			OneWayFlightInfo baseFlight = new OneWayFlightInfo();
			FlightDetail flightDetail = new FlightDetail();
			List<FlightSegement> segs = new ArrayList<FlightSegement>();
			List<String> flightNoList = new ArrayList<String>();
			for (int i = 0; i < ajson.size(); i++) {
				// 航班对象
				JSONObject jsonObject = ajson.getJSONObject(i);
				FlightSegement seg = new FlightSegement();
				// 出发机场三字码
				JSONObject orgObject = jsonObject.getJSONObject("Origin");
				String org = String.valueOf(orgObject.get("Code"));
				// 到达机场三字码
				JSONObject dstObject = jsonObject.getJSONObject("Destination");
				String dst = String.valueOf(dstObject.get("Code"));
				// 航班号
				String flightNumber = String.valueOf(jsonObject.get("FlightNumber"));
				// 起飞日期
				String depDate = String.valueOf(jsonObject.get("DepartureDateString"));
				// 抵达日期
				String arrDate = String.valueOf(jsonObject.get("ArrivalDateString"));
				// 出发时间
				String depTimeString = String.valueOf(jsonObject.get("DepartureTimeString"));
				String depTime = depTimeString.substring(0, depTimeString.lastIndexOf(":"));
				// 到达时间
				String arrTimeString = String.valueOf(jsonObject.get("ArrivalTimeString"));
				String arrTime = arrTimeString.substring(0, arrTimeString.lastIndexOf(":"));
				/**************添加FlightSegement信息****************/
				// 添加信息
				flightNoList.add(flightNumber);
				seg.setFlightno(flightNumber);
				// 起飞日期
				seg.setDepDate(depDate);
				// 抵达日期
				seg.setArrDate(arrDate);
				// 出发机场三字码
				seg.setDepairport(org);
				// 到达机场三字码
				seg.setArrairport(dst);
				// 出发时间
				seg.setDeptime(depTime);
				// 到达时间
				seg.setArrtime(arrTime);
				segs.add(seg);
				
				// 金额对象
				JSONObject jsonPriceObject = ajsonPrice.getJSONObject(i);
				JSONArray faresArray = jsonPriceObject.getJSONArray("Fares");
				JSONObject faresObject = faresArray.getJSONObject(0);
				// 价格
				String amount = String.valueOf(faresObject.get("Amount"));
				// 含税金额
				String amountIncludeTax = String.valueOf(faresObject.get("AmountIncludingTax"));
				// 税
				Double tax = null;
				if(!"".equals(amount) && !"".equals(amountIncludeTax)){
					tax = (double) Math.round((Double.parseDouble(amountIncludeTax) - Double.parseDouble(amount))*100)/100;
				}
				// 货币单位
				String curr = String.valueOf(faresObject.get("Currency"));
				/************添加FlightDetail信息****************/
				// 出发时间
				flightDetail.setDepdate(sdf.parse(depDate));
				// 航班号列表
				flightDetail.setFlightno(flightNoList);
				// 价格，该航班上的最低价
				flightDetail.setPrice(Double.parseDouble(amount));
				// 税
				flightDetail.setTax(tax);
				// 货币单位
				flightDetail.setMonetaryunit(curr);
				// 出发三字码
				flightDetail.setDepcity(org);
				// 到达三字码
				flightDetail.setArrcity(dst);
				// 抓取器id
				flightDetail.setWrapperid(param.getWrapperid());
				// 添加单程航班信息集合
				baseFlight.setDetail(flightDetail);
				baseFlight.setInfo(segs);
				flightList.add(baseFlight);
			}	
			result.setRet(true);
			result.setStatus(Constants.SUCCESS);
			result.setData(flightList);
			return result;
		} catch(Exception e){
			result.setRet(false);
			result.setStatus(Constants.PARSING_FAIL);
			return result;
		}
	}

	public BookingResult getBookingInfo(FlightSearchParam param) {
		String bookingUrlPre = "http://www.bmiregional.com/en/Flight/Search";
		BookingResult bookingResult = new BookingResult();
		BookingInfo bookingInfo = new BookingInfo();
		bookingInfo.setAction(bookingUrlPre);
		bookingInfo.setMethod("get");
		try {
			QFHttpClient httpClient = new QFHttpClient(param, false);
			// 主页html
			String indexUrl = String.format("http://www.bmiregional.com/en");
			QFGetMethod get = new QFGetMethod(indexUrl);
			httpClient.executeMethod(get);
			String indexHtml = get.getResponseBodyAsString();
			String airportSettings = StringUtils.substringBetween(indexHtml, "var airportSettings =", ";");
			// 航空信息转成数组
			JSONArray airportAjson = JSON.parseArray(airportSettings);
			for(int i=0;i<airportAjson.size();i++){
				JSONObject jsonObject = airportAjson.getJSONObject(i);
				airportMap.put(String.valueOf(jsonObject.get("Code")), jsonObject.toString());
			}
			String airportStr = airportMap.get(param.getDep());
			// 货币
			String currency = "";
			if(airportStr != null && !"".equals(airportStr)){
				currency = airportStr.substring(airportStr.indexOf("[")+2, airportStr.indexOf("]")-1);
			}
			// 计算起飞时间相邻一天
			SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
			Calendar cal = Calendar.getInstance();
			Date depDate = sdf.parse(param.getDepDate());
			cal.setTime(depDate);
			cal.add(Calendar.DATE, 1);
			String depTime = sdf.format(cal.getTime());
			Map<String, String> map = new LinkedHashMap<String, String>();
			map.put("interline", "false");
			map.put("fromCityCode", param.getDep());
			map.put("toCityCode", param.getArr());
			map.put("departureDateString", param.getDepDate());
			map.put("returnDateString", depTime);
			map.put("adults", "1");
			map.put("children", "0");
			map.put("infants", "0");
			map.put("roundTrip", "false");
			map.put("useFlexDates", "false");
			map.put("allInclusive", "undefined");
			map.put("promocode", "");
			map.put("fareTypes", "undefined");
			map.put("currency", currency);
			bookingInfo.setInputs(map);
			bookingResult.setData(bookingInfo);
			bookingResult.setRet(true);
		} catch (ParseException e) {
			e.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		}
		return bookingResult;
	}
	
	// 自定义私有类
    private static class MySecureProtocolSocketFactory implements SecureProtocolSocketFactory
    {
    	private SSLContext sslcontext = null;

        private SSLContext createSSLContext()
        {
            SSLContext sslcontext = null;
            try
            {
                sslcontext = SSLContext.getInstance("SSL");
                sslcontext.init(null, new TrustManager[]
                { new TrustAnyTrustManager() }, new java.security.SecureRandom());
            }
            catch (NoSuchAlgorithmException e)
            {
                e.printStackTrace();
            }
            catch (KeyManagementException e)
            {
                e.printStackTrace();
            }
            return sslcontext;
        }

        private SSLContext getSSLContext()
        {
            if (this.sslcontext == null)
            {
                this.sslcontext = createSSLContext();
            }
            return this.sslcontext;
        }

        public Socket createSocket(Socket socket, String host, int port,
                boolean autoClose) throws IOException, UnknownHostException
        {
            return getSSLContext().getSocketFactory().createSocket(socket, host,
                    port, autoClose);
        }

        public Socket createSocket(String host, int port) throws IOException,
                UnknownHostException
        {
            return getSSLContext().getSocketFactory().createSocket(host, port);
        }

        public Socket createSocket(String host, int port, InetAddress clientHost,
                int clientPort) throws IOException, UnknownHostException
        {
            return getSSLContext().getSocketFactory().createSocket(host, port,
                    clientHost, clientPort);
        }

        public Socket createSocket(String host, int port, InetAddress localAddress,
                int localPort, HttpConnectionParams params) throws IOException,
                UnknownHostException, ConnectTimeoutException
        {
            if (params == null)
            {
                throw new IllegalArgumentException("Parameters may not be null");
            }
            int timeout = params.getConnectionTimeout();
            SocketFactory socketfactory = getSSLContext().getSocketFactory();
            if (timeout == 0)
            {
                return socketfactory.createSocket(host, port, localAddress,
                        localPort);
            }
            else
            {
                Socket socket = socketfactory.createSocket();
                SocketAddress localaddr = new InetSocketAddress(localAddress,
                        localPort);
                SocketAddress remoteaddr = new InetSocketAddress(host, port);
                socket.bind(localaddr);
                socket.connect(remoteaddr, timeout);
                return socket;
            }
        }
        
        // 自定义私有类
        private static class TrustAnyTrustManager implements X509TrustManager
        {

            public void checkClientTrusted(X509Certificate[] chain, String authType)
                    throws CertificateException
            {
            }

            public void checkServerTrusted(X509Certificate[] chain, String authType)
                    throws CertificateException
            {
            }

            public X509Certificate[] getAcceptedIssuers()
            {
                return new X509Certificate[]
                {};
            }
        }
    }
    
}
