import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.HttpStatus;
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

/**
 * 途易飞抓取
 * @author Administrator
 *
 */
public class Wrapper_gjsairx3001 implements QunarCrawler{

	public static Map<String,String> cookieMap = new HashMap<String,String>();
	public static Map<String,String> headerMap = new HashMap<String,String>();
	
	// 往返航班
	public static void main(String[] args) {
		FlightSearchParam searchParam = new FlightSearchParam();
		//SXF-KGS 2014-07-12 2014-08-02
		//HAM-ATH 2014-07-19 2014-07-26
		//BRI-ZRH 2014-09-20 2014-09-27
		searchParam.setDep("DUS");
		searchParam.setArr("MAD");
		searchParam.setDepDate("2014-07-15");
		searchParam.setRetDate("2014-08-02");
		searchParam.setTimeOut("60000");
		searchParam.setWrapperid("gjsairx3001");
		searchParam.setToken("");
		
		// 获取请求返回的html
		String html = new  Wrapper_gjsairx3001().getHtml(searchParam);
		ProcessResultInfo result = new ProcessResultInfo();
		// 拼装返回的结果
		result = new  Wrapper_gjsairx3001().process(html,searchParam);
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
		QFGetMethod get = null;	
		try {	
			QFHttpClient httpClient = new QFHttpClient(param, false);
			//对于需要cookie的网站，请自己处理cookie（必须）
			httpClient.getParams().setCookiePolicy(CookiePolicy.BROWSER_COMPATIBILITY);
			// 请求地址
			String getUrl = String.format("https://www.tuifly.com/SearchAndSelect.aspx?culture=en-GB&nextState=select&ADT=1&CHD=0&INF=0&selection=%s",
					param.getDep()+param.getArr()+param.getDepDate().replaceAll("-", "")+param.getRetDate().replaceAll("-", ""));
			get = new QFGetMethod(getUrl);
			// 设置cookie
		    /*Cookie[] cookieInfo = httpClient.getState().getCookies();
		    for(Cookie h : cookieInfo){
		    	headerMap.put(h.getName(), h.getValue());
			}*/
		    //System.out.println(Arrays.toString(httpClient.getState().getCookies()));
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
				String cookie = StringUtils.join(httpClient.getState().getCookies(),"; ");
				cookieMap.put("cookie", cookie);
				get = new QFGetMethod(url);
				httpClient.getState().clearCookies();
				get.addRequestHeader("Cookie",cookie);
				httpClient.executeMethod(get);
				return get.getResponseBodyAsString();
			}
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
		//需要有明显的提示语句，才能判断是否INVALID_DATE|INVALID_AIRLINE|NO_RESULT
		if (html.contains("Es tut uns Leid, in dem gewählten Zeitraum sind leider keine Flüge verfügbar bzw. ausgebucht. Bitte wählen Sie ein anderes Datum oder einen anderen Startflughafen.")) {
			result.setRet(false);
			result.setStatus(Constants.INVALID_DATE);
			return result;
		}
		if (html.contains("Sorry, no flights available. Please select another date or station and try again.")) {
			result.setRet(false);
			result.setStatus(Constants.INVALID_DATE);
			return result;
		}
		try {
			List<RoundTripFlightInfo> flightList = new ArrayList<RoundTripFlightInfo>(); // 往返航班集合
			List<OneWayFlightInfo> goflightList = new ArrayList<OneWayFlightInfo>(); // 去程具体航班信息
			List<OneWayFlightInfo> retflightList = new ArrayList<OneWayFlightInfo>(); // 返程具体航班信息
			
			/*****获取去程信息*****/
			// 去程html片段
			String departHtml = StringUtils.substringBetween(html, "<div class=\"flights qDepartureFlight\">", "<div class=\"flights round qReturnFlight\">");
			// 去程日期
 			String goDateHtml = StringUtils.substringBetween(html,"<label class=\"input date from js-date-container\">","</label>");
			String goDate = StringUtils.substringBetween(goDateHtml,"value=\"","\"").replace(",", "");
			// 截取去程对应html片段
			String goHtml = StringUtils.substringBetween(departHtml, "<div class=\"day\">"+goDate+"</div>", "<div class=\"flightsOfOneDay qFlightsOfOneDay\">");
			// 取出当前日期下有几个航班
			String[] goFlightArr = StringUtils.substringsBetween(goHtml,"data-flightid=\"","\"");
			// 循环去程
			for(int i=0;i<goFlightArr.length;i++){
				// 航班信息
				OneWayFlightInfo baseFlight = new OneWayFlightInfo();
				FlightDetail flightDetail = new FlightDetail();
				List<FlightSegement> segs = new ArrayList<FlightSegement>();
				List<String> flightNoList = new ArrayList<String>();
				// 取去程对应时间的数据
				String goTimeHtml = StringUtils.substringBetween(goHtml,"id=\"flight_"+goFlightArr[i]+"\"","<div class=\"qContent\"></div>");
				String articlenumber = StringUtils.substringBetween(goTimeHtml,"data-articlenumber=\"","\"");
				// 去程出发时间
				String depTime = articlenumber.split("_")[2];
				// 去程中转站html
				String goFreightHtml = StringUtils.substringBetween(goTimeHtml,"<div class=\"cont-taxandfee-flight flightdetails qFlightDetails qTarifChoices\">","<div class=\"flighterror qFlightError resetStyles\">");
				// 截取去程中转站信息
				String[] goZzzArr = StringUtils.substringsBetween(goFreightHtml,"<span class=\"clock\">","</span>");
				if(goZzzArr != null){
					for(int j=0;j<goZzzArr.length;j++){
						FlightSegement goSeg = new FlightSegement();
						// 截取航班详细信息
						String[] fightDetailsHtml = StringUtils.substringsBetween(goFreightHtml,"<div class=\"fightDetailsStopOver\">","</div>");
						// 机场内容
						String airportHtml = StringUtils.substringAfterLast(fightDetailsHtml[j], "<div class=\"flight\">");
						// 出发机场三字码
						String org = StringUtils.substringBetween(airportHtml, "(", ")");
						// 到达机场三字码
						String dst = airportHtml.substring(airportHtml.lastIndexOf("(")+1, airportHtml.lastIndexOf(")"));
						// 航班号
						String airportArea = StringUtils.substringBetween(goFreightHtml,"data-content=\"","\"");
						String[] flightHaoArr = StringUtils.substringsBetween(fightDetailsHtml[j],"data-content=\""+airportArea+"\">","</span>");
						String fliNo = String.valueOf(flightHaoArr[0]).replaceAll("[\\s\"]", "");
						flightNoList.add(fliNo.substring(0,2)+fliNo.substring(3));
						// 航班号列表
						goSeg.setFlightno(fliNo.substring(0,2)+fliNo.substring(3));
						// 起飞日期 
						goSeg.setDepDate(depTime);
						// 抵达日期
						goSeg.setArrDate(depTime);
						// 出发机场三字码
						goSeg.setDepairport(org);
						// 到达机场三字码
						goSeg.setArrairport(dst);
						// 出发时间
						goSeg.setDeptime(goZzzArr[j].substring(0,goZzzArr[j].indexOf("-")-1));
						// 到达时间
						goSeg.setArrtime(goZzzArr[j].substring(goZzzArr[j].indexOf("-")+2));
						// 去程航班
						segs.add(goSeg);
					}
				}
				// 无中转站航班
				else{
					FlightSegement goSeg = new FlightSegement();
					// 出发机场三字码
					String org = StringUtils.substringBetween(goTimeHtml,"data-origincode=\"","\"");
					// 到达机场三字码
					String dst = StringUtils.substringBetween(goTimeHtml,"data-destinationcode=\"","\"");
					// 出发到达片段
					String outInHtml = StringUtils.substringBetween(goTimeHtml,"<li class=\"clock\">","</li>");
					// 航班号
					String flightNo = StringUtils.substringBetween(goTimeHtml,"data-carriercode=\"","\"")+StringUtils.substringBetween(goTimeHtml,"data-flightnumber=\"","\"");
					flightNoList.add(flightNo);
					/**************添加FlightSegement信息****************/
					// 航班号列表
					goSeg.setFlightno(flightNo);
					// 起飞日期
					goSeg.setDepDate(depTime);
					// 抵达日期
					goSeg.setArrDate(depTime);
					// 出发机场三字码
					goSeg.setDepairport(org);
					// 到达机场三字码
					goSeg.setArrairport(dst);
					// 出发时间
					goSeg.setDeptime(outInHtml.substring(0, outInHtml.indexOf("-")));
					// 到达时间
					goSeg.setArrtime(outInHtml.substring(outInHtml.indexOf("-")+1));
					segs.add(goSeg);
				}
				/************添加去程FlightDetail信息****************/
				// 出发时间
				flightDetail.setDepdate(sdf.parse(depTime));
				// 去程含税金额
				String goPriceAndTaxStr = StringUtils.substringBetween(goTimeHtml,"<span class=\"qBruttoPriceAdult\">","</span>");
				double goPriceTax = Double.parseDouble(goPriceAndTaxStr.replace(",", "."));
				flightDetail.setPrice(goPriceTax);
				// 去程税
				flightDetail.setTax(0);
				// 去程币种
				flightDetail.setMonetaryunit("EUR");
				// 航班号列表
				flightDetail.setFlightno(flightNoList);
				// 出发三字码
				flightDetail.setDepcity(param.getDep());
				// 到达三字码
				flightDetail.setArrcity(param.getArr());
				// 抓取器id
				flightDetail.setWrapperid(param.getWrapperid());
				
				// 添加去程航班详细集合
				baseFlight.setDetail(flightDetail);
				baseFlight.setInfo(segs);
				goflightList.add(baseFlight);
			}
				
			/*****获取返程信息*****/
			// 返程html片段
			String returnHtml = StringUtils.substringBetween(html, "<div class=\"flights round qReturnFlight\">", "<div class=\"clear\"/>");
			// 返程日期
			String retDateHtml = StringUtils.substringBetween(html,"<label class=\"input date to perspective js-date-container\">","</label>");
			String retDate = StringUtils.substringBetween(retDateHtml,"value=\"","\"").replace(",", "");
			// 截取返程对应html片段
			String retHtml = StringUtils.substringBetween(returnHtml, "<div class=\"day\">"+retDate+"</div>", "<div class=\"flightsOfOneDay qFlightsOfOneDay\">");
			// 取出当前日期下有几个航班
			String[] retFlightArr = StringUtils.substringsBetween(retHtml,"data-flightid=\"","\"") ;
			// 循环返程
			for(int i=0;i<retFlightArr.length;i++){
				// 航班信息
				OneWayFlightInfo baseFlight = new OneWayFlightInfo();
				FlightDetail flightDetail = new FlightDetail();
				List<FlightSegement> segs = new ArrayList<FlightSegement>();
				List<String> flightNoList = new ArrayList<String>();
				// 取去程对应时间的数据
				String retrunTimeHtml = StringUtils.substringBetween(retHtml,"id=\"flight_"+retFlightArr[i]+"\"","<div class=\"qContent\"></div>");
				String articlenumber = StringUtils.substringBetween(retrunTimeHtml,"data-articlenumber=\"","\"");
				// 返程出发时间
				String retTime = articlenumber.split("_")[2];
				// 返程中转站html
				String retFreightHtml = StringUtils.substringBetween(retrunTimeHtml,"<div class=\"cont-taxandfee-flight flightdetails qFlightDetails qTarifChoices\">","<div class=\"flighterror qFlightError resetStyles\">");
				// 截取返程中转站信息
				String[] returnZzzArr = StringUtils.substringsBetween(retFreightHtml,"<span class=\"clock\">","</span>");
				if(returnZzzArr != null){
					for(int j=0;j<returnZzzArr.length;j++){
						FlightSegement returnSeg = new FlightSegement();
						// 截取航班详细信息
						String[] fightDetailsHtml = StringUtils.substringsBetween(retFreightHtml,"<div class=\"fightDetailsStopOver\">","</div>");
						// 机场内容
						String airportHtml = StringUtils.substringAfterLast(fightDetailsHtml[j], "<div class=\"flight\">");
						// 出发机场三字码
						String org = StringUtils.substringBetween(airportHtml, "(", ")");
						// 到达机场三字码
						String dst = airportHtml.substring(airportHtml.lastIndexOf("(")+1, airportHtml.lastIndexOf(")"));
						// 航班号
						String airportArea = StringUtils.substringBetween(retFreightHtml,"data-content=\"","\"");
						String[] flightHaoArr = StringUtils.substringsBetween(fightDetailsHtml[j],"data-content=\""+airportArea+"\">","</span>");
						String fliNo = String.valueOf(flightHaoArr[0]).replaceAll("[\\s\"]", "");
						flightNoList.add(fliNo.substring(0,2)+fliNo.substring(3));
						// 航班号列表
						returnSeg.setFlightno(fliNo.substring(0,2)+fliNo.substring(3));
						// 起飞日期
						returnSeg.setDepDate(retTime);
						// 抵达日期
						returnSeg.setArrDate(retTime);
						// 出发机场三字码
						returnSeg.setDepairport(org);
						// 到达机场三字码
						returnSeg.setArrairport(dst);
						// 出发时间
						returnSeg.setDeptime(returnZzzArr[j].substring(0,returnZzzArr[j].indexOf("-")-1));
						// 到达时间
						returnSeg.setArrtime(returnZzzArr[j].substring(returnZzzArr[j].indexOf("-")+2));
						// 去程航班
						segs.add(returnSeg);
					}
				}
				// 无中转站航班
				else{
					FlightSegement goSeg = new FlightSegement();
					// 出发机场三字码
					String org = StringUtils.substringBetween(retrunTimeHtml,"data-origincode=\"","\"");
					// 到达机场三字码
					String dst = StringUtils.substringBetween(retrunTimeHtml,"data-destinationcode=\"","\"");
					// 出发到达片段
					String outInHtml = StringUtils.substringBetween(retrunTimeHtml,"<li class=\"clock\">","</li>");
					// 航班号
					String flightNo = StringUtils.substringBetween(retrunTimeHtml,"data-carriercode=\"","\"")+StringUtils.substringBetween(retrunTimeHtml,"data-flightnumber=\"","\"");
					flightNoList.add(flightNo);
					/**************添加FlightSegement信息****************/
					// 航班号列表
					goSeg.setFlightno(flightNo);
					// 起飞日期
					goSeg.setDepDate(retTime);
					// 抵达日期
					goSeg.setArrDate(retTime);
					// 出发机场三字码
					goSeg.setDepairport(org);
					// 到达机场三字码
					goSeg.setArrairport(dst);
					// 出发时间
					goSeg.setDeptime(outInHtml.substring(0, outInHtml.indexOf("-")));
					// 到达时间
					goSeg.setArrtime(outInHtml.substring(outInHtml.indexOf("-")+1));
					segs.add(goSeg);
				}
				/************添加返程FlightDetail信息****************/
				// 出发时间
				flightDetail.setDepdate(sdf.parse(retTime));
				// 返程含税金额
				String retPriceAndTaxStr = StringUtils.substringBetween(retrunTimeHtml,"<span class=\"qBruttoPriceAdult\">","</span>");
				double retPriceTax = Double.parseDouble(retPriceAndTaxStr.replace(",", "."));
				flightDetail.setPrice(retPriceTax);
				// 去程税
				flightDetail.setTax(0);
				// 去程币种
				flightDetail.setMonetaryunit("EUR");
				// 航班号列表
				flightDetail.setFlightno(flightNoList);
				// 出发三字码
				flightDetail.setDepcity(param.getDep());
				// 到达三字码
				flightDetail.setArrcity(param.getArr());
				// 抓取器id
				flightDetail.setWrapperid(param.getWrapperid());
				
				// 添加返程航班详细集合
				baseFlight.setDetail(flightDetail);
				baseFlight.setInfo(segs);
				retflightList.add(baseFlight);
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

						List<FlightSegement> goreSegs = new ArrayList<FlightSegement>();
						// 创建往返航班对象
						RoundTripFlightInfo rtf = new RoundTripFlightInfo();
						rtf.setRetdepdate(reDetail.getDepdate()); // 返程日期
						rtf.setRetflightno(reDetail.getFlightno()); // 返程航班号列表
						rtf.setRetinfo(reSegs); // 返程航班信息列表
						rtf.setOutboundPrice(goDetail.getPrice()); // 去程价格
						rtf.setReturnedPrice(reDetail.getPrice()); // 返程价格

						FlightDetail goreDetail = new FlightDetail();
						// 往返详细
						goreDetail.setDepcity(goDetail.getDepcity());
						goreDetail.setArrcity(goDetail.getArrcity());
						goreDetail.setDepdate(goDetail.getDepdate());
						List<String> flightnoAll = Lists.newArrayList();
						flightnoAll.addAll(goDetail.getFlightno());
						flightnoAll.addAll(reDetail.getFlightno());
						goreDetail.setFlightno(flightnoAll);
						goreDetail.setMonetaryunit(goDetail.getMonetaryunit());
						goreDetail.setTax(sum(goDetail.getTax(), reDetail.getTax()));
						goreDetail.setPrice(sum(goDetail.getPrice(), reDetail.getPrice()));
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
		// https://www.tuifly.com/SearchAndSelect.aspx?culture=en-GB&nextState=select&ADT=1&CHD=0&INF=0&selection=SXFKGS2014071220140802
		String bookingUrlPre = "https://www.tuifly.com/SearchAndSelect.aspx";
		BookingResult bookingResult = new BookingResult();
		BookingInfo bookingInfo = new BookingInfo();
		bookingInfo.setAction(bookingUrlPre);
		bookingInfo.setMethod("get");	
		Map<String, String> map = new LinkedHashMap<String, String>();
		map.put("culture", "en-GB");
		map.put("nextState", "select");
		map.put("ADT", "1");
		map.put("CHD", "0");
		map.put("INF", "0");
		map.put("selection", param.getDep()+param.getArr()+param.getDepDate().replaceAll("-", "")+param.getRetDate().replaceAll("-", ""));
		bookingInfo.setInputs(map);
		bookingResult.setData(bookingInfo);
		bookingResult.setRet(true);
		return bookingResult;
	}
}
