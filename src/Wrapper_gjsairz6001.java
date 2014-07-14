import java.math.BigDecimal;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.cookie.CookiePolicy;
import org.apache.commons.lang.StringUtils;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.google.common.collect.Lists;
import com.qunar.qfwrapper.bean.booking.BookingInfo;
import com.qunar.qfwrapper.bean.booking.BookingResult;
import com.qunar.qfwrapper.bean.search.FlightDetail;
import com.qunar.qfwrapper.bean.search.FlightSearchParam;
import com.qunar.qfwrapper.bean.search.FlightSegement;
import com.qunar.qfwrapper.bean.search.OneWayFlightInfo;
import com.qunar.qfwrapper.bean.search.ProcessResultInfo;
import com.qunar.qfwrapper.bean.search.RoundTripFlightInfo;
import com.qunar.qfwrapper.constants.Constants;
import com.qunar.qfwrapper.interfaces.QunarCrawler;
import com.qunar.qfwrapper.util.QFGetMethod;
import com.qunar.qfwrapper.util.QFHttpClient;
import com.qunar.qfwrapper.util.QFPostMethod;

/**
 * 华沙肖邦航空抓取
 * @author Administrator
 *
 */
public class Wrapper_gjsairz6001 implements QunarCrawler{

	public static Map<String,String> cookieMap = new HashMap<String,String>();
	
	// 往返航班
	public static void main(String[] args) {
		FlightSearchParam searchParam = new FlightSearchParam();
		//KBP HRK 2014-07-14 2014-08-02
		//DNK KBP 2014-07-14 2014-07-31
		//BKK TBS 2014-07-14 2014-07-31
		searchParam.setDep("DNK");
		searchParam.setArr("KBP");
		searchParam.setDepDate("2014-07-14");
		searchParam.setRetDate("2014-07-31");
		searchParam.setTimeOut("60000");
		searchParam.setWrapperid("gjsairz6001");
		searchParam.setToken("");
		
		// 获取请求返回的html
		String html = new  Wrapper_gjsairz6001().getHtml(searchParam);
		ProcessResultInfo result = new ProcessResultInfo();
		// 拼装返回的结果
		result = new  Wrapper_gjsairz6001().process(html,searchParam);
		System.out.println(com.alibaba.fastjson.JSON.toJSONString(result,
				SerializerFeature.DisableCircularReferenceDetect));
		if(result.isRet() && result.getStatus().equals(Constants.SUCCESS))
		{
			List<RoundTripFlightInfo> flightList = (List<RoundTripFlightInfo>) result.getData();
			for (RoundTripFlightInfo in : flightList){
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
		QFPostMethod post = null;
		QFGetMethod get = null;
		try {	
			QFHttpClient httpClient = new QFHttpClient(param, false);
			//对于需要cookie的网站，请自己处理cookie（必须）
			httpClient.getParams().setCookiePolicy(CookiePolicy.BROWSER_COMPATIBILITY);
			// 第一次请求
			String postUrl = "http://booking.dniproavia.com/index.php";
			post = new QFPostMethod(postUrl);
			String[] depArray = param.getDepDate().split("-");
			String depDate = depArray[2]+"."+depArray[1]+"."+depArray[0];
			String[] arrArray = param.getRetDate().split("-");
			String retDate = arrArray[2]+"."+arrArray[1]+"."+arrArray[0];
			post.addParameter("data[returnTicket]", "on");
			post.addParameter("data[flights][0][originSearch]", param.getDep());
			post.addParameter("data[flights][0][destinationSearch]", param.getArr());
			post.addParameter("data[flights][0][departureDateInput]", depDate);
			post.addParameter("data[flights][0][departureTime]", "");
			post.addParameter("data[flights][1][departureDateInput]", retDate);
			post.addParameter("data[flights][1][departureTime]", "");
			post.addParameter("data[cabin]", "");
			post.addParameter("data[travelersCount][ADT]", "1");
			post.addParameter("data[travelersCount][CHD]", "0");
			post.addParameter("data[travelersCount][INF]", "0");
			post.addParameter("data[lastStep]", "OneMultiple");
			post.addParameter("action", "eSearchFormData");
			post.addParameter("data[vendor]", "");
			post.addParameter("data[searchType]", "FromFour");
			post.addParameter("data[travelersCount][YCD]", "0");
			post.addParameter("data[travelersCount][YTH]", "0");
			post.addParameter("data[selectionCounter]", "");
			post.addParameter("data[stepSearchTypeRedirect]", "Continue");
			post.setFollowRedirects(false);
			post.getParams().setContentCharset("UTF-8");
			int postState = httpClient.executeMethod(post);
			if(postState == 303){
				Header location = post.getResponseHeader("Location");
				String url = "";
				if(location !=null){
					url = location.getValue();
					if(!url.startsWith("http")){
						url = post.getURI().getScheme() + "://" + post.getURI().getHost() + (post.getURI().getPort()==-1?"":(":"+post.getURI().getPort())) + url;
					}
				}else{
					return "";
				}
				// 第二次请求
				String cookie = StringUtils.join(httpClient.getState().getCookies(),"; ");
				get = new QFGetMethod(url);
				httpClient.getState().clearCookies();
				get.addRequestHeader("Cookie",cookie);
				httpClient.executeMethod(get);
				// 第三次请求
				cookie = StringUtils.join(httpClient.getState().getCookies(),"; ");
				String formatUrl = "http://booking.dniproavia.com/?action=vFlights&flights[0][departureDate]="+param.getDepDate()+"&flights[0][destination]="+param.getArr()+"&flights[0][origin]="+param.getDep()+"&flights[1][departureDate]="+param.getRetDate()+"&flights[1][destination]="+param.getDep()+"&flights[1][origin]="+param.getArr()+"&travelers[0]=ADT&returnTicket=on&lang=en&step=ChooseFromFour";
				String getUrl = formatUrl.replaceAll("\\[", URLEncoder.encode("[","utf-8")).replaceAll("\\]",URLEncoder.encode("]","utf-8"));
				get = new QFGetMethod(getUrl);
				httpClient.getState().clearCookies();
				get.setRequestHeader("Referer", url);
				get.addRequestHeader("Cookie",cookie);
				get.setFollowRedirects(false);
				httpClient.executeMethod(get);
				return get.getResponseBodyAsString();
			}
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
			List<RoundTripFlightInfo> flightList = new ArrayList<RoundTripFlightInfo>(); // 往返航班集合
			List<OneWayFlightInfo> goflightList = new ArrayList<OneWayFlightInfo>(); // 去程具体航班信息
			List<OneWayFlightInfo> retflightList = new ArrayList<OneWayFlightInfo>(); // 返程具体航班信息
			
			// 查询去程多组航班
			String[] goReferencesStr = StringUtils.substringsBetween(html, "data[urlodAO3AvailReference]", "class=\"inputText\"");
			for(int ii=0;ii<goReferencesStr.length;ii++){
				// 去程航班信息
				OneWayFlightInfo goBaseFlight = new OneWayFlightInfo();
				List<FlightSegement> goSegs = new ArrayList<FlightSegement>();
				List<String> goFlightNoList = new ArrayList<String>();
				// 关系内容
				String references = goReferencesStr[ii];
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
					goFlightNoList.add(flightNumber);
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
					goSegs.add(seg);
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
				flightDetail.setFlightno(goFlightNoList);
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
				goBaseFlight.setDetail(flightDetail);
				goBaseFlight.setInfo(goSegs);
				goflightList.add(goBaseFlight);
			}
			
			// 查询返程多组航班
			String[] retReferencesStr = StringUtils.substringsBetween(html, "data[urldoAO3AvailReference]", "class=\"inputText\"");
			for(int ii=0;ii<retReferencesStr.length;ii++){
				// 返程航班信息
				OneWayFlightInfo retBaseFlight = new OneWayFlightInfo();
				List<FlightSegement> retSegs = new ArrayList<FlightSegement>();
				List<String> retFlightNoList = new ArrayList<String>();
				// 关系内容
				String references = retReferencesStr[ii];
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
					retFlightNoList.add(flightNumber);
					seg.setFlightno(flightNumber);
					// 起飞日期
					seg.setDepDate(param.getRetDate());
					// 抵达日期
					seg.setArrDate(param.getRetDate());
					// 出发机场三字码
					seg.setDepairport(dst);
					// 到达机场三字码
					seg.setArrairport(org);
					// 出发时间
					seg.setDeptime(depTime);
					// 到达时间
					seg.setArrtime(arrTime);
					retSegs.add(seg);
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
				flightDetail.setDepdate(sdf.parse(param.getRetDate()));
				// 航班号列表
				flightDetail.setFlightno(retFlightNoList);
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
				retBaseFlight.setDetail(flightDetail);
				retBaseFlight.setInfo(retSegs);
				retflightList.add(retBaseFlight);
			}
			
			/*************拼接去程和返程航班信息******************/
			if (goflightList != null && goflightList.size() > 0 && 
					retflightList != null && retflightList.size() > 0) {
				for (int i = 0; i < goflightList.size(); i++) {
					for (int j = 0; j < retflightList.size(); j++) {
						// 去程
						List<FlightSegement> goSegs = goflightList.get(i).getInfo();
						FlightDetail goDetail = goflightList.get(i).getDetail();
						// 返程
						List<FlightSegement> reSegs = retflightList.get(j).getInfo();
						FlightDetail reDetail = retflightList.get(j).getDetail();

						FlightDetail goreDetail = new FlightDetail();
						List<FlightSegement> goreSegs = new ArrayList<FlightSegement>();

						// 创建往返航班对象
						RoundTripFlightInfo rtf = new RoundTripFlightInfo();
						rtf.setRetdepdate(reDetail.getDepdate()); // 返程日期
						rtf.setRetflightno(reDetail.getFlightno()); // 返程航班号列表
						rtf.setRetinfo(reSegs); // 返程航班信息列表
						rtf.setOutboundPrice(goDetail.getPrice()); // 去程价格
						rtf.setReturnedPrice(reDetail.getPrice()); // 返程价格

						// 往返详细
						goreDetail.setDepcity(goDetail.getDepcity());
						goreDetail.setArrcity(goDetail.getArrcity());
						goreDetail.setDepdate(goDetail.getDepdate());
						List<String> flightnoAll = Lists.newArrayList();
						flightnoAll.addAll(goDetail.getFlightno());
						flightnoAll.addAll(reDetail.getFlightno());
						goreDetail.setFlightno(flightnoAll);
						goreDetail.setMonetaryunit(goDetail.getMonetaryunit());
						goreDetail.setTax(reDetail.getTax());
						goreDetail.setPrice(reDetail.getPrice());
						goreDetail.setWrapperid(goDetail.getWrapperid());
						// 添加去程
						goreSegs.addAll(goSegs);
						// 添加返程
						goreSegs.addAll(reSegs);
						rtf.setDetail(goreDetail);
						rtf.setInfo(goreSegs);
						flightList.add(rtf);
					}
				}
			} else {
				result.setRet(false);
				result.setStatus(Constants.PARSING_FAIL);
				return result;
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

	public double sum(double d1, double d2) {
		BigDecimal bd1 = new BigDecimal(Double.toString(d1));
		BigDecimal bd2 = new BigDecimal(Double.toString(d2));
		return bd1.add(bd2).doubleValue();
	}
	
	public BookingResult getBookingInfo(FlightSearchParam param) {
		String bookingUrlPre = "http://booking.dniproavia.com/?";
		BookingResult bookingResult = new BookingResult();
		BookingInfo bookingInfo = new BookingInfo();
		bookingInfo.setAction(bookingUrlPre);
		bookingInfo.setMethod("get");
		Map<String, String> map = new LinkedHashMap<String, String>();
		map.put("action", "vFlights");
		map.put("flights[0][departureDate]", param.getDepDate());
		map.put("flights[0][destination]", param.getArr());
		map.put("flights[0][origin]", param.getDep());
		map.put("flights[1][departureDate]", param.getRetDate());
		map.put("flights[1][destination]", param.getDep());
		map.put("flights[1][origin]", param.getArr());
		map.put("travelers[0]", "ADT");
		map.put("returnTicket", "on");
		map.put("dateVariants", "exact");
		map.put("step", "ChooseFromFour");
		map.put("lang", "en");
		bookingInfo.setInputs(map);
		bookingResult.setData(bookingInfo);
		bookingResult.setRet(true);
		return bookingResult;
	}
	
}
