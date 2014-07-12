import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.httpclient.cookie.CookiePolicy;
import org.apache.commons.lang.StringUtils;
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

/**
 * 华沙肖邦航空抓取
 * @author Administrator
 *
 */
public class Wrapper_gjdairz6001 implements QunarCrawler{

	public static Map<String,String> cookieMap = new HashMap<String,String>();
	
	// 单程航班
	public static void main(String[] args) {
		FlightSearchParam searchParam = new FlightSearchParam();
		//KBP DNK 2014-07-23
		//VAR BUS 2014-07-12
		//IFO DNK 2014-07-16
		searchParam.setDep("IFO");
		searchParam.setArr("DNK");
		searchParam.setDepDate("2014-07-16");
		searchParam.setTimeOut("60000");
		searchParam.setWrapperid("gjdairz6001");
		searchParam.setToken("");
		// 获取请求返回的html
		String html = new  Wrapper_gjdairz6001().getHtml(searchParam);
		ProcessResultInfo result = new ProcessResultInfo();
		// 拼装返回的结果
		result = new  Wrapper_gjdairz6001().process(html,searchParam);
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
	
	public String getHtml(FlightSearchParam param) {
		QFGetMethod get = null;
		try {	
			QFHttpClient httpClient = new QFHttpClient(param, false);
			//对于需要cookie的网站，请自己处理cookie（必须）
			httpClient.getParams().setCookiePolicy(CookiePolicy.BROWSER_COMPATIBILITY);
			// 第一步请求地址
			String oneUrl = "http://booking.dniproavia.com/?action=vFlights&flights[0][departureDate]="+param.getDepDate()+"&flights[0][destination]="+param.getDep()+"&flights[0][origin]="+param.getArr()+"&travelers[0]=ADT&returnTicket=&step=ChooseFromFour";
			// 第二步请求地址
			String forUrl = String.format("?action=vFlights&flights[0][departureDate]=%s&flights[0][destination]=%s&flights[0][origin]=%s&travelers[0]=ADT&returnTicket=&step=ChooseFromFour&lang=en",param.getDepDate(),param.getDep(),param.getArr());
			String twoUrl = "http://booking.dniproavia.com/index.php"+URLEncoder.encode(forUrl,"UTF-8");
			get = new QFGetMethod(twoUrl);
			httpClient.executeMethod(get);
			
			// 获取cookie
			get = new QFGetMethod(twoUrl);
			String cookie = StringUtils.join(httpClient.getState().getCookies(),"; ");
			httpClient.getState().clearCookies();
			get.addRequestHeader("Cookie",cookie);
			get.setRequestHeader("Referer", oneUrl);
			httpClient.executeMethod(get);
			return get.getResponseBodyAsString();
		} catch (Exception e) {			
			e.printStackTrace();
		} finally{
			if(null != get){
				get.releaseConnection();
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
		if(html.contains("No available seats found for the criteria entered. Please modify the requirements or select another travel class.")){
			result.setRet(false);
			result.setStatus(Constants.NO_RESULT);
			return result;
		}
		try {
			// 单程航班信息
			List<OneWayFlightInfo> flightList = new ArrayList<OneWayFlightInfo>();
			// 查询多组航班
			String[] referencesStr = StringUtils.substringsBetween(html, "data[urlodAO3AvailReference]", "class=\"inputText\"");
			for(int ii=0;ii<referencesStr.length;ii++){
				OneWayFlightInfo baseFlight = new OneWayFlightInfo();
				List<FlightSegement> segs = new ArrayList<FlightSegement>();
				List<String> flightNoList = new ArrayList<String>();
				// 关系内容
				String references = referencesStr[ii];
				// 关系名称
				String refName = StringUtils.substringBetween(references,"[","]").substring(3);
				// 关系值
				String refValue = StringUtils.substringBetween(references,"value=\"","\"");
				String urlReference = URLEncoder.encode(refValue,"UTF-8");
				String segHtml = StringUtils.substringBetween(html, "seg.urlReference = '"+urlReference+"';", "seg.stops = new Array();");
				// 航班次信息
				String[] flightInfoHtml = StringUtils.substringsBetween(html, "el.reference = '"+refName+"';", "el.durationMouseOver");
				for(int i=0;i<flightInfoHtml.length;i++){
					// 航班次信息
					String flightInfo = flightInfoHtml[i];
					// 航班号
					String flightNumber = StringUtils.substringBetween(flightInfo, "el.vendorCode = '", "';")+StringUtils.substringBetween(flightInfo, "el.flightNumber = '", "';");
					// 出发机场三字码
					String dst = StringUtils.substringBetween(flightInfo, "el.destination = '", "';");
					// 到达机场三字码
					String org = StringUtils.substringBetween(flightInfo, "el.origin = '", "';");
					// 出发时间
					String depTime = StringUtils.substringBetween(flightInfo, "el.departureTime = '", "';");
					// 到达时间
					String arrTime = StringUtils.substringBetween(flightInfo, "el.arrivalTime = '", "';");
					/************添加FlightSegement信息****************/
					FlightSegement seg = new FlightSegement();
					// 添加信息
					flightNoList.add(flightNumber);
					seg.setFlightno(flightNumber);
					// 起飞日期
					seg.setDepDate(param.getDepDate());
					// 抵达日期
					seg.setArrDate(param.getDepDate());
					// 出发机场三字码
					seg.setDepairport(dst);
					// 到达机场三字码
					seg.setArrairport(org);
					// 出发时间
					seg.setDeptime(depTime);
					// 到达时间
					seg.setArrtime(arrTime);
					segs.add(seg);
				}
				// 价格
				String amount = StringUtils.substringBetween(segHtml, "seg.priceBase = ", ";");
				// 含税金额
				String amountIncludeTax = StringUtils.substringBetween(segHtml, "seg.priceTotal = ", ";");
				// 税
				Double tax = null;
				if(!"".equals(amount) && !"".equals(amountIncludeTax)){
					tax = (double) Math.round((Double.parseDouble(amountIncludeTax) - Double.parseDouble(amount))*100)/100;
				}
				// 货币单位
				String currency = StringUtils.substringBetween(segHtml, "seg.currency = '", "';");
				/************添加FlightDetail信息****************/
				FlightDetail flightDetail = new FlightDetail();
				// 出发时间
				flightDetail.setDepdate(sdf.parse(param.getDepDate()));
				// 航班号列表
				flightDetail.setFlightno(flightNoList);
				// 价格，该航班上的最低价
				flightDetail.setPrice(Double.parseDouble(amount));
				// 税
				flightDetail.setTax(tax);
				// 货币单位
				flightDetail.setMonetaryunit(currency);
				// 出发三字码
				flightDetail.setDepcity(param.getDep());
				// 到达三字码
				flightDetail.setArrcity(param.getArr());
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
		String bookingUrlPre = "http://booking.dniproavia.com/index.php";
		BookingResult bookingResult = new BookingResult();
		BookingInfo bookingInfo = new BookingInfo();
		bookingInfo.setAction(bookingUrlPre);
		bookingInfo.setMethod("get");	
		Map<String, String> map = new LinkedHashMap<String, String>();
		map.put("action", "vFlights");
		map.put("flights[0][departureDate]", param.getDepDate());
		map.put("flights[0][destination]", param.getDep());
		map.put("flights[0][origin]", param.getArr());
		map.put("travelers[0]", "ADT");
		map.put("returnTicket", "");
		map.put("step", "ChooseFromFour");
		map.put("lang", "en");
		bookingInfo.setInputs(map);
		bookingResult.setData(bookingInfo);
		bookingResult.setRet(true);
		return bookingResult;
	}
	
}
