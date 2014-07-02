import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang.StringUtils;
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
 * 途易飞抓取
 * @author Administrator
 *
 */
public class Wrapper_gjdairx3001 implements QunarCrawler{
	
	// 单程航班
	public static void main(String[] args) {
		FlightSearchParam searchParam = new FlightSearchParam();
		//SXF-KGS 2014-07-12
		//HAM-ATH 2014-07-19
		//BRI-ZRH 2014-09-20
		searchParam.setDep("SXF");
		searchParam.setArr("KGS");
		searchParam.setDepDate("2014-07-12");
		searchParam.setTimeOut("60000");
		searchParam.setWrapperid("gjdairx3001");
		searchParam.setToken("");
		
		// 获取请求返回的html
		String html = new  Wrapper_gjdairx3001().getHtml(searchParam);
		ProcessResultInfo result = new ProcessResultInfo();
		// 拼装返回的结果
		result = new  Wrapper_gjdairx3001().process(html,searchParam);
		//System.out.println(com.alibaba.fastjson.JSON.toJSONString(result,
		//		SerializerFeature.DisableCircularReferenceDetect));
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
	
	public BookingResult getBookingInfo(FlightSearchParam param) {
		// https://www.tuifly.com/en/search?origin=SXF&destination=KGS&start=2014-07-12&sort=PriceAsc&triptype=oneway&duration=7&adults=1&children=0&infants=0&carrier=DE
		String bookingUrlPre = "http://www.tuifly.com/en/index.html";
		BookingResult bookingResult = new BookingResult();
		
		BookingInfo bookingInfo = new BookingInfo();
		bookingInfo.setAction(bookingUrlPre);
		bookingInfo.setMethod("get");	
		Map<String, String> map = new LinkedHashMap<String, String>();
		map.put("origin", param.getDep());
		map.put("destination", param.getArr());
		map.put("start", param.getDepDate().replaceAll("-", "/"));
		map.put("sort", "PriceAsc");
		map.put("triptype", "oneway");
		map.put("duration", "07");
		map.put("adults", "1");
		map.put("children", "0");
		map.put("infants", "0");
		map.put("carrier", "DE");
		bookingInfo.setInputs(map);
		bookingResult.setData(bookingInfo);
		bookingResult.setRet(true);
		return bookingResult;
	}

	public String getHtml(FlightSearchParam param) {
		QFGetMethod get = null;	
		try {	
			QFHttpClient httpClient = new QFHttpClient(param, false);
			
			/*对于需要cookie的网站，请自己处理cookie（必须）
			* httpClient.getParams().setCookiePolicy(CookiePolicy.BROWSER_COMPATIBILITY);
			*/
			// 请求地址
			String getUrl = String.format("https://www.tuifly.com/en/search?origin=%s&destination=%s&start=%s&sort=PriceAsc&triptype=oneway&duration=7&adults=1&children=0&infants=0",
					param.getDep(), param.getArr(), param.getDepDate());
			//String getUrl = "https://www.tuifly.com/en/search?origin=SXF&destination=KGS&start=2014-07-12&sort=PriceAsc&triptype=oneway&duration=7&adults=1&children=0&infants=0&carrier=DE";
			
			get = new QFGetMethod(getUrl);
			
			//1、对于通过多次get|post请求才能得到包含机票信息的网站，需要注意对status的判断
			//2、对于通过多次get|post请求才能得到包含机票信息的网站，如果需要cookie，则在每一次get|post请求前都处理好cookie
			//3、如果网站需要使用cookie，GetMethod 遇到 302 时默认会自动跳转，不留机会给 开发处理Cookie，这个时候要特别小心， 需要使用 get.setFollowRedirects(false); 阻止自动跳转，然后自己处理302 以及Cookie。
			/* 例如：
			try {
				get.setFollowRedirects(false);
				get.getParams().setContentCharset("utf-8");
				client.executeMethod(get);
			
				if(get.getStatusCode() == HttpStatus.SC_MOVED_TEMPORARILY || get.getStatusCode() == HttpStatus.SC_MOVED_PERMANENTLY){
					Header location = get.getResponseHeader("Location");
					String url = "";
					if(location !=null){
						url = location.getValue();
						if(!url.startsWith("http")){
							url = get.getURI().getScheme() + "://" + get.getURI().getHost() + (get.getURI().getPort()==-1?"":(":"+get.getURI().getPort())) + url;
						}
					}else{
						return;
					}
					String cookie = StringUtils.join(client.getState().getCookies(),"; ");
					get = new QFGetMethod(url);
					client.getState().clearCookies();
					get.addRequestHeader("Cookie",cookie);
					client.executeMethod(get);
				}
			} catch (Exception e) {
			e.printStackTrace();
			} finally {
				if(get!=null){
					get.releaseConnection();
				}
			}
			 */
		    httpClient.executeMethod(get);
		    return get.getResponseBodyAsString();
		} catch (Exception e) {			
			e.printStackTrace();
		} finally{
			if (null != get){
				get.releaseConnection();
			}
		}
		return "Exception";
	}

	public ProcessResultInfo process(String html, FlightSearchParam param) {
		String htmlStr = html;
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
		QFHttpClient httpClient = new QFHttpClient(param, false);
		/* ProcessResultInfo中，
		 * ret为true时，status可以为：SUCCESS(抓取到机票价格)|NO_RESULT(无结果，没有可卖的机票)
		 * ret为false时，status可以为:CONNECTION_FAIL|INVALID_DATE|INVALID_AIRLINE|PARSING_FAIL|PARAM_ERROR
		 */
		ProcessResultInfo result = new ProcessResultInfo();
		if ("Exception".equals(htmlStr)) {	
			result.setRet(false);
			result.setStatus(Constants.CONNECTION_FAIL);
			return result;			
		}		
		//需要有明显的提示语句，才能判断是否INVALID_DATE|INVALID_AIRLINE|NO_RESULT
		if (htmlStr.contains("Unfortunately there are no flights available in the time period you selected.")) {
			result.setRet(false);
			result.setStatus(Constants.INVALID_DATE);
			return result;			
		}
		try {
			// 第二次次请求
			String resultStr = StringUtils.substringBetween(htmlStr, "<tbody class=\"js-tbody\">", "</tbody>");
			String requestUrl = StringUtils.substringBetween(resultStr,"<td class=\"cell button-cell more js-select\">","</td>");
			String hrefUrl = StringUtils.substringBetween(requestUrl,"href=\"","\"").replaceAll("amp;", "");
			// 请求地址
			//https://www.tuifly.com/SearchAndSelect.aspx?culture=en-GB&nextState=select&ADT=1&CHD=0&INF=0&selection=BRIZRH20140920
			QFGetMethod getMethod = new QFGetMethod(hrefUrl);
			httpClient.executeMethod(getMethod);
			// 返回的html片段
			String myHtml = getMethod.getResponseBodyAsString();
			// 判断是否有可用航班
			if (myHtml.contains("Es tut uns Leid, in dem gewählten Zeitraum sind leider keine Flüge verfügbar bzw. ausgebucht. Bitte wählen Sie ein anderes Datum oder einen anderen Startflughafen.")) {
				// Sorry, no flights available. Please select another date or station and try again.
				result.setRet(false);
				result.setStatus(Constants.INVALID_DATE);
				return result;
			}
			if (myHtml.contains("Sorry, no flights available. Please select another date or station and try again.")) {
				result.setRet(false);
				result.setStatus(Constants.INVALID_DATE);
				return result;
			}
			// 去程html片段
			String departHtml = StringUtils.substringBetween(myHtml, "<div class=\"flights qDepartureFlight\">", "<div class=\"noFlights qNoResultsForFilter hidden\">");
			System.out.println(departHtml);
			// 转换起飞时间格式如：05.07.14
			String[] depDateArr = param.getDepDate().split("-");
	    	String depDate = depDateArr[2]+"."+depDateArr[1]+"."+depDateArr[0].substring(2);
			// 截取对应时间的html片段
	    	String ddHtml = StringUtils.substringBetween(departHtml, "<div class=\"day\">Sa "+depDate+"</div>", "<div class=\"flightsOfOneDay qFlightsOfOneDay\">");
	    	// 取出当前日期下有几个航班
			String[] flightArr = StringUtils.substringsBetween(ddHtml,"data-flightid=\"","\"") ;
			List<OneWayFlightInfo> flightList = new ArrayList<OneWayFlightInfo>();
			// 循环去程航班
			for(int i=0;i<flightArr.length;i++){
				// 单程航班信息
				OneWayFlightInfo baseFlight = new OneWayFlightInfo();
				FlightDetail flightDetail = new FlightDetail();
				List<FlightSegement> segs = new ArrayList<FlightSegement>();
				List<String> flightNoList = new ArrayList<String>();
				// 取对应时间的数据
				String subHtml = StringUtils.substringBetween(ddHtml,"id=\"flight_"+flightArr[i]+"\"","<div class=\"qContent\"></div>");
				String articlenumber = StringUtils.substringBetween(subHtml,"data-articlenumber=\"","\"");
				// 出发时间
				String depTime = articlenumber.split("_")[2];
				// 截取中转站信息
				String[] zzzArr = StringUtils.substringsBetween(subHtml,"<span class=\"clock\">","</span>");
				if(zzzArr != null && zzzArr.length > 1){
					for(int j=0;j<zzzArr.length;j++){
						FlightSegement seg = new FlightSegement();
						// 截取航班详细信息
						String[] fightDetailsHtml = StringUtils.substringsBetween(subHtml,"<div class=\"fightDetailsStopOver\">","</div>");
						// 机场内容
						String airportHtml = StringUtils.substringAfterLast(fightDetailsHtml[j], "<div class=\"flight\">");
						// 出发机场三字码
						String org = StringUtils.substringBetween(airportHtml, "(", ")");
						// 到达机场三字码
						String dst = airportHtml.substring(airportHtml.lastIndexOf("(")+1, airportHtml.lastIndexOf(")"));
						// 航班号
						String airportArea = StringUtils.substringBetween(subHtml,"data-content=\"","\"");
						String[] flightHaoArr = StringUtils.substringsBetween(fightDetailsHtml[j],"data-content=\""+airportArea+"\">","</span>");
						String fliNo = String.valueOf(flightHaoArr[0]).replaceAll("[\\s\"]", "");
						//System.out.println(String.valueOf(flightHaoArr[0]).replaceAll(String.valueOf((char)160), ""));
						flightNoList.add(fliNo.substring(0,2)+fliNo.substring(3));
						/**************添加FlightSegement信息****************/
						// 航班号列表
						seg.setFlightno(fliNo.substring(0,2)+fliNo.substring(3));
						// 起飞日期
						seg.setDepDate(depTime);
						// 抵达日期
						seg.setArrDate(depTime);
						// 出发机场三字码
						seg.setDepairport(org);
						// 到达机场三字码
						seg.setArrairport(dst);
						// 出发时间
						seg.setDeptime(zzzArr[j].substring(0,zzzArr[j].indexOf("-")-1));
						// 到达时间
						seg.setArrtime(zzzArr[j].substring(zzzArr[j].indexOf("-")+2));
						segs.add(seg);
					}
				}
				// 无中转站航班
				else{
					FlightSegement seg = new FlightSegement();
					// 出发机场三字码
					String org = StringUtils.substringBetween(subHtml,"data-origincode=\"","\"");
					// 到达机场三字码
					String dst = StringUtils.substringBetween(subHtml,"data-destinationcode=\"","\"");
					// 出发到达片段
					String outInHtml = StringUtils.substringBetween(subHtml,"<li class=\"clock\">","</li>");
					// 航班号
					String flightNo = StringUtils.substringBetween(subHtml,"data-carriercode=\"","\"")+StringUtils.substringBetween(subHtml,"data-flightnumber=\"","\"");
					flightNoList.add(flightNo);
					/**************添加FlightSegement信息****************/
					// 航班号列表
					seg.setFlightno(flightNo);
					// 起飞日期
					seg.setDepDate(depTime);
					// 抵达日期
					seg.setArrDate(depTime);
					// 出发机场三字码
					seg.setDepairport(org);
					// 到达机场三字码
					seg.setArrairport(dst);
					// 出发时间
					seg.setDeptime(outInHtml.substring(0, outInHtml.indexOf("-")));
					// 到达时间
					seg.setArrtime(outInHtml.substring(outInHtml.indexOf("-")+1));
					segs.add(seg);
				}
				/************添加FlightDetail信息****************/
				// 出发时间
				flightDetail.setDepdate(sdf.parse(depTime));
				// 第三次请求获取金额和税
				String sellkey = StringUtils.substringBetween(subHtml,"data-sellkey=\"","\"");
				String flightid = StringUtils.substringBetween(subHtml,"data-flightid=\"","\"");
				String encodeUrl = String.format("https://www.tuifly.com/TaxAndFeeInclusiveDisplay-resource.aspx?flightKeys=%s&uniqueFlightRequestKey=%s",URLEncoder.encode(sellkey,"UTF-8"),flightid);
				// 请求地址
				//String pHref = "https://www.tuifly.com/TaxAndFeeInclusiveDisplay-resource.aspx?flightKeys=0~O~~X3~ODE~5100~~0~2~~X|DE~6628~ ~~SXF~07/12/2014 15:10~KGS~07/12/2014 19:10~~&uniqueFlightRequestKey=1.3.1";
				QFGetMethod getMethod1 = new QFGetMethod(encodeUrl);
				httpClient.executeMethod(getMethod1);
				// 返回金额和税的html片段
				String priceHtml = getMethod1.getResponseBodyAsString();
				if(null != getMethod1){
					getMethod1.releaseConnection();
				}
				// 航班号列表
				flightDetail.setFlightno(flightNoList);
				// 价格，该航班上的最低价
				String goPriceHtml = StringUtils.substringBetween(priceHtml,"<li class=\"summery _json-ADT\">","</li>");
				String priceStr = StringUtils.substringBetween(goPriceHtml,"<span class=\"rate _json-totalPrice\">","€");
				String priceD = priceStr.substring(1,priceStr.length()-1).replace(",", ".");
				double price = Double.parseDouble(priceD);
				flightDetail.setPrice(price);
				// 税
				String goTaxHtml = StringUtils.substringBetween(priceHtml,"<li class=\"taxes _json-taxes\">","</li>");
				String taxStr = StringUtils.substringBetween(goTaxHtml,"<span class=\"rate _json-totalPrice\">","€");
				String taxD = taxStr.substring(1,taxStr.length()-1).replace(",", ".");
				double tax = Double.parseDouble(taxD);
				flightDetail.setTax(tax);
				// 货币单位
				String cur = StringUtils.substringBetween(priceHtml,"currency=\"","\"");
				flightDetail.setMonetaryunit(cur);
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
			if(null != getMethod){
				getMethod.releaseConnection();
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

}
