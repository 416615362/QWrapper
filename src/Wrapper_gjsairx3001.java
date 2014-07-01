
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.httpclient.Cookie;
import org.apache.commons.lang.StringUtils;
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

public class Wrapper_gjsairx3001 implements QunarCrawler{

	public static Map<String,String> cookieMap = new HashMap<String,String>();
	public static Map<String,String> headerMap = new HashMap<String,String>();
	
	// 往返航班
	public static void main(String[] args) {
		FlightSearchParam searchParam = new FlightSearchParam();
		//SXF-KGS 2014-07-12 2014-08-02
		//HAM-ATH 2014-07-19 2014-07-26
		//BRI-ZRH 2014-09-20 2014-09-27
		searchParam.setDep("HAM");
		searchParam.setArr("ATH");
		searchParam.setDepDate("2014-07-19");
		searchParam.setRetDate("2014-07-26");
		searchParam.setTimeOut("60000");
		searchParam.setWrapperid("gjsairx3001");
		searchParam.setToken("");
		
		// 获取请求返回的html
		String html = new  Wrapper_gjsairx3001().getHtml(searchParam);
		ProcessResultInfo result = new ProcessResultInfo();
		// 拼装返回的结果
		result = new  Wrapper_gjsairx3001().process(html,searchParam);
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

	@Override
	public String getHtml(FlightSearchParam param) {
		QFGetMethod get = null;	
		try {	
			QFHttpClient httpClient = new QFHttpClient(param, false);
			// 请求地址
			String getUrl = String.format("https://www.tuifly.com/SearchAndSelect.aspx?culture=en-GB&nextState=select&ADT=1&CHD=0&INF=0&selection=%s",
					param.getDep()+param.getArr()+param.getDepDate().replaceAll("-", "")+param.getRetDate().replaceAll("-", ""));
			get = new QFGetMethod(getUrl);
		    httpClient.executeMethod(get);
		    
		    /*Header [] headers = get.getResponseHeaders() ;
	        String cookies = "" ;
			for(Header h : headers){
				headerMap.put(h.getName(), h.getValue()) ;
				if("Cookie".equalsIgnoreCase(h.getName())){
					cookies += h.getValue() ;
				}
			}*/
		    //Arrays.toString()
			// 设置cookie
		    Cookie[] cookieInfo = httpClient.getState().getCookies();
		    for(Cookie h : cookieInfo){
		    	headerMap.put(h.getName(), h.getValue());
			}
		    //System.out.println(Arrays.toString(httpClient.getState().getCookies()));
		    String htmlStr = get.getResponseBodyAsString();
		    return htmlStr;
		} catch (Exception e) {			
			e.printStackTrace();
		} finally{
			if (null != get){
				get.releaseConnection();
			}
		}
		return "Exception";
	}

	@Override
	public ProcessResultInfo process(String html, FlightSearchParam param) {
		String htmlStr = html;
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
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
		if (htmlStr.contains("Es tut uns Leid, in dem gewählten Zeitraum sind leider keine Flüge verfügbar bzw. ausgebucht. Bitte wählen Sie ein anderes Datum oder einen anderen Startflughafen.")) {
			// Sorry, no flights available. Please select another date or station and try again.
			result.setRet(false);
			result.setStatus(Constants.INVALID_DATE);
			return result;
		}
		try {
			List<RoundTripFlightInfo> flightList = new ArrayList<RoundTripFlightInfo>(); // 往返航班集合
			List<OneWayFlightInfo> goflightList = new ArrayList<OneWayFlightInfo>(); // 去程具体航班信息
			List<OneWayFlightInfo> retflightList = new ArrayList<OneWayFlightInfo>(); // 返程具体航班信息
			
			// 存储去程航班金额信息
			Map<String,Object> goFlightMap = new HashMap<String,Object>();
			// 存储返程航班金额信息
			Map<String,Object> retFlightMap = new HashMap<String,Object>();
			
			/*****获取去程信息*****/
			// 去程html片段
			String departHtml = StringUtils.substringBetween(htmlStr, "<div class=\"flights qDepartureFlight\">", "<div class=\"flights round qReturnFlight\">");
			// 去程时间格式转化为：05.07.14
			String[] goDateArr = param.getDepDate().split("-");
			String goDate = goDateArr[2]+"."+goDateArr[1]+"."+goDateArr[0].substring(2);
			// 截取去程对应html片段
			String goHtml = StringUtils.substringBetween(departHtml, "<div class=\"day\">Sa "+goDate+"</div>", "<div class=\"flightsOfOneDay qFlightsOfOneDay\">");
			// 取出当前日期下有几个航班
			String[] goFlightArr = StringUtils.substringsBetween(goHtml,"data-flightid=\"","\"");
			// 5个去程
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
				// 截取去程中转站信息
				String[] goZzzArr = StringUtils.substringsBetween(goTimeHtml,"<span class=\"clock\">","</span>");
				if(goZzzArr != null){
					for(int j=0;j<goZzzArr.length;j++){
						FlightSegement goSeg = new FlightSegement();
						// 截取航班详细信息
						String[] fightDetailsHtml = StringUtils.substringsBetween(goTimeHtml,"<div class=\"fightDetailsStopOver\">","</div>");
						// 机场内容
						String airportHtml = StringUtils.substringAfterLast(fightDetailsHtml[j], "<div class=\"flight\">");
						// 出发机场三字码
						String org = StringUtils.substringBetween(airportHtml, "(", ")");
						// 到达机场三字码
						String dst = airportHtml.substring(airportHtml.lastIndexOf("(")+1, airportHtml.lastIndexOf(")"));
						// 航班号
						String airportArea = StringUtils.substringBetween(goTimeHtml,"data-content=\"","\"");
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
				
				// 去程请求获取金额和税
				String goSellkey = StringUtils.substringBetween(goTimeHtml,"data-sellkey=\"","\"");
				String goFlightid = StringUtils.substringBetween(goTimeHtml,"data-flightid=\"","\"");
				
				// 返程html片段
				String returnHtml = StringUtils.substringBetween(htmlStr, "<div class=\"flights round qReturnFlight\">", "<div class=\"clear\"></div>");
				// 返程时间格式转化为：26.07.14
				String[] retDateArr = param.getRetDate().split("-");
				String retDate = retDateArr[2]+"."+retDateArr[1]+"."+retDateArr[0].substring(2);
				// 截取返程对应html片段
				String retHtml = StringUtils.substringBetween(returnHtml, "<div class=\"day\">Sa "+retDate+"</div>", "<div class=\"flightsOfOneDay qFlightsOfOneDay\">");
				// 取出当前日期下有几个航班
				String[] retFlightArr = StringUtils.substringsBetween(retHtml,"data-flightid=\"","\"") ;
				// 3个返程
				for(int j=0;j<retFlightArr.length;j++){
					// 取去程对应时间的数据
					String retrunTimeHtml = StringUtils.substringBetween(retHtml,"id=\"flight_"+retFlightArr[j]+"\"","<div class=\"qContent\"></div>");
					// 返程请求获取金额和税
					String retSellkey = StringUtils.substringBetween(retrunTimeHtml,"data-sellkey=\"","\"");
					String retFlightid = StringUtils.substringBetween(retrunTimeHtml,"data-flightid=\"","\"");
					// 拼接的请求参数
					String sellkey = goSellkey+","+retSellkey;
					String flightid = goFlightid+"::"+retFlightid;
					/*********第二次请求*************/
					// 请求地址
					String requestUrl = String.format("https://www.tuifly.com/TaxAndFeeInclusiveDisplay-resource.aspx?flightKeys=%s&uniqueFlightRequestKey=%s",URLEncoder.encode(sellkey,"UTF-8"),URLEncoder.encode(flightid,"UTF-8"));
					//String requestUrl = "https://www.tuifly.com/TaxAndFeeInclusiveDisplay-resource.aspx?flightKeys="+sellkey+"&uniqueFlightRequestKey="+flightid;
					QFHttpClient httpClient = new QFHttpClient(param, false);
					QFGetMethod getMethod = new QFGetMethod(requestUrl);
					getMethod.setRequestHeader("Accept","text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
					getMethod.setRequestHeader("Accept-Charset","GBK,utf-8;q=0.7,*;q=0.3");
					getMethod.setRequestHeader("Accept-Encoding","gzip,deflate,sdch");
					getMethod.setRequestHeader("Accept-Language","zh-CN,zh;q=0.8");
					getMethod.setRequestHeader("Cache-Control","max-age=0");
					getMethod.setRequestHeader("Connection","keep-alive");
					getMethod.setRequestHeader("Cookie","optimizelyEndUserId=oeu1401846454002r0.6400896897539496; ABTesting=TarifDesignPreselect=B; ASP.NET_SessionId="+headerMap.get("ASP.NET_SessionId")+"; POPUPCHECK=1403927102495; s_cc=true; __utma=79925266.1273851147.1401846456.1403840636.1403847999.12; __utmb=79925266.5.10.1403847999; __utmc=79925266; __utmz=79925266.1401846456.1.1.utmcsr=(direct)|utmccn=(direct)|utmcmd=(none); __utmv=79925266.|1=searcher=searched=1^5=flight=searched=1; s_vi=[CS]v1|29C73D61851D0DF9-60000107C00259F2[CE]; notepadCount=0; s_nr=1403850289326-Repeat; undefined_s=First%20Visit; s_sq=tuifly-produktion-en%3D%2526pid%253Dindex.html%2526pidt%253D1%2526oid%253DFIND%252520FLIGHTS%2526oidt%253D3%2526ot%253DSUBMIT; user-profile=%7B%22customer%3Asearch%3Ahistory%22%3A%5B%7B%22oneway%22%3Afalse%2C%22airports%22%3A%7B%22origin%22%3A%5B%22HAM%22%5D%2C%22destination%22%3A%5B%22ATH%22%5D%7D%2C%22dates%22%3A%7B%22duration%22%3Anull%2C%22start%22%3A%222014-07-19%22%2C%22end%22%3A%222014-07-26%22%7D%2C%22passengers%22%3A%7B%22adults%22%3A1%2C%22children%22%3A0%2C%22infants%22%3A0%7D%7D%2C%7B%22oneway%22%3Afalse%2C%22airports%22%3A%7B%22origin%22%3A%5B%22HAM%22%5D%2C%22destination%22%3A%5B%22ATH%22%5D%7D%2C%22dates%22%3A%7B%22duration%22%3Anull%2C%22start%22%3A%222014-07-19%22%2C%22end%22%3A%222014-07-26%22%7D%2C%22passengers%22%3A%7B%22adults%22%3A1%2C%22children%22%3A0%2C%22infants%22%3A0%7D%7D%5D%7D; AvailabilitySearchData=UnxIQU18QVRIfDE5fDIwMTQtMDd8MjZ8MjAxNC0wN3wx; loginEvent=1; optimizelySegments=%7B%22211157978%22%3A%22false%22%2C%22211934000%22%3A%22none%22%2C%22211989614%22%3A%22gc%22%2C%22211998475%22%3A%22direct%22%7D; optimizelyBuckets=%7B%7D; uzchkcookie=; sID=skysales.tfl-s12");
					getMethod.setRequestHeader("Host","www.tuifly.com");
					getMethod.setRequestHeader("User-Agent","Mozilla/5.0 (Windows NT 6.1) AppleWebKit/537.1 (KHTML, like Gecko) Chrome/21.0.1180.89 Safari/537.1");
					httpClient.executeMethod(getMethod);
					// 返回金额和税的html片段
					String priceHtml = getMethod.getResponseBodyAsString();
					if(null != getMethod){
						getMethod.releaseConnection();
					}
					// 获取去程金额html
					String journey1Html = StringUtils.substringBetween(priceHtml,"class=\"SMILE-Tarif journey1 _json-outbound\"","</ul>");
					// 去程价格
					String goPriceHtml = StringUtils.substringBetween(journey1Html,"<li class=\"summery _json-ADT\">","</li>");
					String goPriceStr = StringUtils.substringBetween(goPriceHtml,"<span class=\"rate _json-totalPrice\">","€");
					String goPriceD = goPriceStr.substring(1,goPriceStr.length()-1).replace(",", ".");
					// 去程税
					String goTaxHtml = StringUtils.substringBetween(journey1Html,"<li class=\"taxes _json-taxes\">","</li>");
					String goTaxStr = StringUtils.substringBetween(goTaxHtml,"<span class=\"rate _json-totalPrice\">","€");
					String goTaxD = goTaxStr.substring(1,goTaxStr.length()-1).replace(",", ".");
					// 货币单位
					String cur = StringUtils.substringBetween(journey1Html,"currency=\"","\"");
					// 存储去程金额，税，币种
					goFlightMap.put(goFlightid, goPriceD+","+goTaxD+","+cur);
					
					// 获取返程金额html
					String journey2Html = StringUtils.substringBetween(priceHtml,"class=\"SMILE-Tarif journey2 _json-inbound\"","</ul>");
					// 返程价格
					String retPriceHtml = StringUtils.substringBetween(journey2Html,"<li class=\"summery _json-ADT\">","</li>");
					String retPriceStr = StringUtils.substringBetween(retPriceHtml,"<span class=\"rate _json-totalPrice\">","€");
					String retPriceD = retPriceStr.substring(1,retPriceStr.length()-1).replace(",", ".");
					// 返程税
					String retTaxHtml = StringUtils.substringBetween(journey2Html,"<li class=\"taxes _json-taxes\">","</li>");
					String retTaxStr = StringUtils.substringBetween(retTaxHtml,"<span class=\"rate _json-totalPrice\">","€");
					String retTaxD = retTaxStr.substring(1,retTaxStr.length()-1).replace(",", ".");
					double retTax = Double.parseDouble(retTaxD);
					// 存储返程金额，税，币种
					retFlightMap.put(retFlightid, retPriceD+","+retTax+","+cur);
				}
				
				/************添加去程FlightDetail信息****************/
				// 出发时间
				flightDetail.setDepdate(sdf.parse(depTime));
				// 去程数组
				String[] flightids = String.valueOf(goFlightMap.get(goFlightid)).split(",");
				// 去程金额
				flightDetail.setPrice(Double.parseDouble(flightids[0]));
				// 去程税
				flightDetail.setTax(Double.parseDouble(flightids[1]));
				// 去程币种
				flightDetail.setMonetaryunit(flightids[2]);
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
			String returnHtml = StringUtils.substringBetween(htmlStr, "<div class=\"flights round qReturnFlight\">", "<div class=\"clear\"></div>");
			// 返程时间格式转化为：26.07.14
			String[] retDateArr = param.getRetDate().split("-");
			String retDate = retDateArr[2]+"."+retDateArr[1]+"."+retDateArr[0].substring(2);
			// 截取返程对应html片段
			String retHtml = StringUtils.substringBetween(returnHtml, "<div class=\"day\">Sa "+retDate+"</div>", "<div class=\"flightsOfOneDay qFlightsOfOneDay\">");
			// 取出当前日期下有几个航班
			String[] retFlightArr = StringUtils.substringsBetween(retHtml,"data-flightid=\"","\"") ;
			// 3个返程
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
				// 截取返程中转站信息
				String[] returnZzzArr = StringUtils.substringsBetween(retrunTimeHtml,"<span class=\"clock\">","</span>");
				if(returnZzzArr != null){
					for(int j=0;j<returnZzzArr.length;j++){
						FlightSegement returnSeg = new FlightSegement();
						// 截取航班详细信息
						String[] fightDetailsHtml = StringUtils.substringsBetween(retrunTimeHtml,"<div class=\"fightDetailsStopOver\">","</div>");
						// 机场内容
						String airportHtml = StringUtils.substringAfterLast(fightDetailsHtml[j], "<div class=\"flight\">");
						// 出发机场三字码
						String org = StringUtils.substringBetween(airportHtml, "(", ")");
						// 到达机场三字码
						String dst = airportHtml.substring(airportHtml.lastIndexOf("(")+1, airportHtml.lastIndexOf(")"));
						// 航班号
						String airportArea = StringUtils.substringBetween(retrunTimeHtml,"data-content=\"","\"");
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
				/************添加返程FlightDetail信息****************/
				// 出发时间
				flightDetail.setDepdate(sdf.parse(retTime));
				// 请求获取金额和税
				String flightid = StringUtils.substringBetween(retrunTimeHtml,"data-flightid=\"","\"");
				// 返程数组
				String[] flightids = String.valueOf(retFlightMap.get(flightid)).split(",");
				// 去程金额
				flightDetail.setPrice(Double.parseDouble(flightids[0]));
				// 去程税
				flightDetail.setTax(Double.parseDouble(flightids[1]));
				// 去程币种
				flightDetail.setMonetaryunit(flightids[2]);
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
						goreDetail.setTax(goDetail.getTax());
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

	@Override
	public BookingResult getBookingInfo(FlightSearchParam param) {
		// https://www.tuifly.com/en/search?origin=SXF&destination=KGS&start=2014-07-12&sort=PriceAsc&triptype=oneway&duration=7&adults=1&children=0&infants=0&carrier=DE
		String bookingUrlPre = "http://www.tuifly.com/en/index.html";
		BookingResult bookingResult = new BookingResult();
		
		BookingInfo bookingInfo = new BookingInfo();
		bookingInfo.setAction(bookingUrlPre);
		bookingInfo.setMethod("post");	
		Map<String, String> map = new LinkedHashMap<String, String>();
		map.put("ro", "0");
		map.put("from", param.getDep());
		map.put("to", param.getArr());
		map.put("cur", "EUR");
		map.put("sdate", param.getDepDate().replaceAll("-", "/"));
		map.put("edate", param.getDepDate().replaceAll("-", "/"));
		map.put("adult", "1");
		map.put("child", "0");
		map.put("infant", "0");
		map.put("view", "0");
		map.put("btnsubmit", "Flight Search");
		bookingInfo.setInputs(map);
		bookingResult.setData(bookingInfo);
		bookingResult.setRet(true);
		return bookingResult;
	}
	
	public double sum(double d1, double d2) {
		BigDecimal bd1 = new BigDecimal(Double.toString(d1));
		BigDecimal bd2 = new BigDecimal(Double.toString(d2));
		return bd1.add(bd2).doubleValue();
	}
}
