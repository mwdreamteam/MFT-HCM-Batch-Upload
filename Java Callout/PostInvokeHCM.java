package com.oracle.mft.sample.hcm;
/*
javac -classpath $MW_HOME/mft/modules/oracle/mft/core.jar <class>
jar cf PostInvokeHCM.jar
*/
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringWriter;
import java.io.Writer;

import java.util.Map;

import oracle.tip.mft.bean.Instance;
import oracle.tip.mft.bean.SourceMessage;
import oracle.tip.mft.bean.TargetMessage;
import oracle.tip.mft.engine.processsor.plugin.PluginContext;
import oracle.tip.mft.engine.processsor.plugin.PostCalloutPlugin;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;
import javax.xml.soap.*;
import javax.xml.transform.*;
import javax.xml.transform.stream.*;

/*
 * Shell for MFT invoking HCM Load SOAP api in Post Target cllout
 */
public class PostInvokeHCM implements PostCalloutPlugin {
  private String hcmendpoint = "";
  private String hcmuser = "";
  private String hcmpassword = "";
  private String useFilenameForDocid = "true";

    public PostInvokeHCM() {
        super();
    }

    @Override
    public void process(PluginContext context, InputStream inputStream,
                        Map<String, String> calloutParams) throws Exception {

		    PluginContext.getLogger().info("PostInvokeHCM.process: Starting HCM SOAP Call Post Transfer", "");
        //System.out.println("Starting HCM SOAP Call Post Transfer");
        hcmendpoint = calloutParams.get("hcmendpoint").trim();
        hcmuser = calloutParams.get("hcmuser").trim();
        hcmpassword = calloutParams.get("hcmpassword").trim();
        useFilenameForDocid = calloutParams.get("useFilenameForDocid");

        PluginContext.getLogger().info("PostInvokeHCM.process: ",
                                       "hcmendpoint: " + hcmendpoint +
                                       " hcmuser:" + hcmuser +
                                       " useFilenameForDocid: " +useFilenameForDocid
                                       );

		//Verifying the parameters recieved
		//System.out.println("Starting HCM SOAP Call Post Transfer");
		    PluginContext.getLogger().info("PostInvokeHCM.process: URL "+hcmendpoint, "");
		    PluginContext.getLogger().info("PostInvokeHCM.process: Username "+hcmuser, "");
		    PluginContext.getLogger().info("PostInvokeHCM.process: Password *******", "");

        //extract File Transfer Params
        TargetMessage targetMessage = (TargetMessage)context.getMessage();
        Instance transferInstance = targetMessage.getInstance();
        SourceMessage sourceMessage = transferInstance.getTransferInstance().getSourceMessage();

		//Get the object representing the file transferred and received at the Target
        File targetFile = targetMessage.getDataStorage().getPayloadFile() ;
        String createdTime = transferInstance.getCpstInstCreatedTime().toString();
        String transferName = transferInstance.getTransferName();
        String transferSource = sourceMessage.getSourceName();
        String transferTarget =transferInstance.getTargetName();
        String filename = targetFile.getName(); //targetMessage.getDeliveredFileName();

		    //Verify the file name received
		    PluginContext.getLogger().info("PostInvokeHCM postHCM File Name Recieved ", filename);

		    //Read other parameters related to the File
        String path = targetFile.getPath();
        String dir = targetFile.getParent();
        String size = targetMessage.getDataStorage().getPayloadSize().toString();
        String uploadedDocId = context.getCustomPropertyMap().get("UploadedDoc_dID");
        PluginContext.getLogger().info("PostInvokeHCM.process: uploadedDocId=", uploadedDocId);

        // update some protocol headers in the dashboard
        setHeaders(context);
        //Send the request
        try {
          String mydocid = useFilenameForDocid.equals("true") ? filename : uploadedDocId;
          postHCM(mydocid, hcmendpoint, hcmuser, hcmpassword);
        } catch (Exception e) {
          PluginContext.getLogger().info("PostInvokeHCM postHCM Error: " , e.toString());
          throw new RuntimeException(e);
        }
    }

    /*
     * invoke HCM
    */
    private String postHCM(String docid,
                          String hcmendpoint,
                          String hcmuser,
                          String hcmpassword)
                    throws Exception {
      String resp = "OK";
      PluginContext.getLogger().info("PostInvokeHCM postHCM args: " , "docid=" +docid +" endpoint=" +hcmendpoint +" user=" +hcmuser);

  		// Create SOAP Connection
	  	SOAPConnectionFactory soapConnectionFactory = SOAPConnectionFactory.newInstance();
		  SOAPConnection soapConnection = soapConnectionFactory.createConnection();

		  // Send SOAP Message to SOAP Server
		  String url = hcmendpoint; // BAD to add this +"?WSDL" for SOAP_1_2;
	        SOAPMessage soapResponse;	
		try {      
		 	soapResponse = soapConnection.call(createSOAPRequest(docid,hcmuser,hcmpassword), url);
		}
                catch (SOAPException e) {
                   System.out.println(e.getMessage());
                   throw e;
                }

		  // Process the SOAP Response
		  printSOAPResponse(soapResponse);
      PluginContext.getLogger().info("PostInvokeHCM postHCM closing soapConnection " , "");
		  soapConnection.close();
      // return default response
      PluginContext.getLogger().info("PostInvokeHCM postHCM returning: " , resp);

      return resp;
    }

    /*
     * Add Headers to "Protocol Headers in MFT Console UI"
    */
    private void setHeaders(PluginContext context) {
        Map<String, String> customProperties = context.getCustomPropertyMap();
        customProperties.put("hcmendpoint", hcmendpoint);
        customProperties.put("hcmuser", hcmuser);
        customProperties.put("hcmpassword", "*********");
    }

    public SOAPMessage createSOAPRequest(String docid, String hcmuser,String hcmpassword) throws Exception {
        MessageFactory messageFactory = MessageFactory.newInstance();
        SOAPMessage soapMessage = messageFactory.createMessage();
        SOAPPart soapPart = soapMessage.getSOAPPart();

		   // Declaring the namespaces to be used
        String soapSrvrURI="http://schemas.xmlsoap.org/soap/envelope/";
        String typsSrvrURI="http://xmlns.oracle.com/apps/hcm/common/dataLoader/core/dataLoaderIntegrationService/types/";
        //String typsSrvrURI="http://xmlns.oracle.com/apps/hcm/common/dataLoader/core/dataLoaderIntegrationService/";

        // SOAP Envelope
        SOAPEnvelope envelope = soapPart.getEnvelope();

        envelope.addNamespaceDeclaration("soapenv", soapSrvrURI);
        envelope.addNamespaceDeclaration("typ", typsSrvrURI);

        /*
        Below is the SOAP Message to be constructed:

        <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:typ="http://xmlns.oracle.com/apps/hcm/common/dataLoader/core/dataLoaderIntegrationService/types/">
           <soapenv:Header>
              <wsse:Security soapenv:mustUnderstand="1" xmlns:wsse="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd" xmlns:wsu="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-utility-1.0.xsd">
                 <wsu:Timestamp wsu:Id="TS-C7A210ACC44117937914876680282948">
                    <wsu:Created>2017-02-21T09:07:08.294Z</wsu:Created>
                    <wsu:Expires>2017-02-21T09:12:08.294Z</wsu:Expires>
                 </wsu:Timestamp>
                 <wsse:UsernameToken wsu:Id="UsernameToken-91DE8B265F9E2E234314876481355586">
                    <wsse:Username>HCM_IMPL</wsse:Username>
                    <wsse:Password Type="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-username-token-profile-1.0#PasswordText">KXD69443</wsse:Password>
                    <wsse:Nonce EncodingType="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-soap-message-security-1.0#Base64Binary">vva9bOnkH5X4z4aEsPVUfw==</wsse:Nonce>
                    <wsu:Created>2017-02-21T03:35:35.558Z</wsu:Created>
                 </wsse:UsernameToken>
              </wsse:Security>
           </soapenv:Header>
           <soapenv:Body>
              <typ:importAndLoadData>
                 <typ:ContentId>ucmDataLoader.zip</typ:ContentId>
              </typ:importAndLoadData>
           </soapenv:Body>
        </soapenv:Envelope>
         */

        // SOAP Body
        SOAPBody soapBody = envelope.getBody();
        SOAPElement soapBodyElem = soapBody.addChildElement("importAndLoadData", "typ");
        SOAPElement soapBodyElem1 = soapBodyElem.addChildElement("ContentId", "typ");
        soapBodyElem1.addTextNode(docid);

        //*****Security Header Section******
        /*<wsu:Timestamp wsu:Id="TS-C7A210ACC44117937914876680282948">
           <wsu:Created>2017-02-21T09:07:08.294Z</wsu:Created>
           <wsu:Expires>2017-02-21T09:12:08.294Z</wsu:Expires>
        </wsu:Timestamp>*/

        soapMessage.getSOAPHeader().addNamespaceDeclaration("wsse", "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd");
        soapMessage.getSOAPHeader().addNamespaceDeclaration("wsu", "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-utility-1.0.xsd");

        SOAPElement securityElem1=soapMessage.getSOAPHeader().addChildElement("Security", "wsse").addAttribute(envelope.createName("env:mustUnderstand"), "1");
        SOAPElement securityElem2=securityElem1.addChildElement("Timestamp", "wsu");
        //securityElem2.addAttribute(envelope.createName("wsu:Id"), "TS-C7A210ACC44117937914876680282948");

        SimpleDateFormat format = new SimpleDateFormat("EEE MMM dd hh:mm:ss Z yyyy");
        Date stDate=format.parse(new Date().toString());
        Date edDate=format.parse(new Date().toString());

        Calendar now = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        String time="";
        String addedTime="";

		    //Setting the Time, consideringthe the UTC Timezone
        time="T"+ (now.get(Calendar.HOUR_OF_DAY)>9 ? now.get(Calendar.HOUR_OF_DAY) : "0"+now.get(Calendar.HOUR_OF_DAY)) + ":"+ (now.get(Calendar.MINUTE)>9 ? now.get(Calendar.MINUTE) : "0"+now.get(Calendar.MINUTE)) + ":" + (now.get(Calendar.SECOND)>9 ? now.get(Calendar.SECOND): "0"+now.get(Calendar.SECOND))+ "." + now.get(Calendar.MILLISECOND)+"Z";
        //The Timestamp expires after 100 seconds of its creation
		    now.add(Calendar.SECOND, 100);
        addedTime="T"+ (now.get(Calendar.HOUR_OF_DAY)>9 ? now.get(Calendar.HOUR_OF_DAY) : "0"+now.get(Calendar.HOUR_OF_DAY)) + ":"+ (now.get(Calendar.MINUTE)>9 ? now.get(Calendar.MINUTE) : "0"+now.get(Calendar.MINUTE)) + ":" + (now.get(Calendar.SECOND)>9 ? now.get(Calendar.SECOND): "0"+now.get(Calendar.SECOND))+ "." + now.get(Calendar.MILLISECOND)+"Z";

        SimpleDateFormat format1 = new SimpleDateFormat("yyyy-MM-dd");
        SOAPElement securityElem3=securityElem2.addChildElement("Created", "wsu");
        securityElem3.addTextNode(format1.format(stDate)+time);
        SOAPElement securityElem4=securityElem2.addChildElement("Expires", "wsu");
        securityElem4.addTextNode(format1.format(edDate)+addedTime);

        //*****UserName, Pwd and Nonce section*****
        /*<wsse:UsernameToken wsu:Id="UsernameToken-91DE8B265F9E2E234314876481355586">
           <wsse:Username>HCM_IMPL</wsse:Username>
           <wsse:Password Type="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-username-token-profile-1.0#PasswordText">KXD69443</wsse:Password>
           <wsse:Nonce EncodingType="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-soap-message-security-1.0#Base64Binary">vva9bOnkH5X4z4aEsPVUfw==</wsse:Nonce>
           <wsu:Created>2017-02-21T03:35:35.558Z</wsu:Created>
        </wsse:UsernameToken>*/

        SOAPElement userElem1=securityElem1.addChildElement("UsernameToken", "wsse").addAttribute(envelope.createName("wsu:Id"), "UsernameToken-91DE8B265F9E2E234314876481355586");
        SOAPElement userElem2=userElem1.addChildElement("Username", "wsse");
        userElem2.addTextNode(hcmuser);
        SOAPElement userElem3=userElem1.addChildElement("Password", "wsse").addAttribute(envelope.createName("Type"), "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-username-token-profile-1.0#PasswordText");
        userElem3.addTextNode(hcmpassword);
        SOAPElement userElem4=userElem1.addChildElement("Nonce", "wsse").addAttribute(envelope.createName("EncodingType"), "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-soap-message-security-1.0#Base64Binary");
        userElem4.addTextNode("vva9bOnkH5X4z4aEsPVUfw==");
        SOAPElement userElem5=userElem1.addChildElement("Created", "wsu");
        userElem5.addTextNode(format1.format(stDate)+time);

        MimeHeaders headers = soapMessage.getMimeHeaders();
        headers.addHeader("SOAPAction", "http://xmlns.oracle.com/apps/hcm/common/dataLoader/core/dataLoaderIntegrationService/importAndLoadData");

        
soapMessage.saveChanges();

        /* Print the request message */
        System.out.print("Request SOAP Message = ");
        soapMessage.writeTo(System.out);
        System.out.println();
        //PluginContext.getLogger().info("\createSOAPRequest: Request SOAP Message = ", soapMessage.toString());
        return soapMessage;
    }

    /**
     * Method used to print the SOAP Response
     */
    private static void printSOAPResponse(SOAPMessage soapResponse) throws Exception {
//PluginContext.getLogger().info(
        //System.out.println(soapResponse.getSOAPBody().getFault());
        //System.out.println(soapResponse.getSOAPBody().getFault()!=null);
        if(soapResponse.getSOAPBody().getFault()!=null){
            String fault = soapResponse.getSOAPBody().getFault().toString();
            PluginContext.getLogger().info("printSOAPResponse: fault is: ", fault);
            throw new Exception("printSOAPResponse FAULT: " +fault);
            //throw new Exception("Wrong credentials: Kindly check");
        }
        else {
            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            Transformer transformer = transformerFactory.newTransformer();
            Source sourceContent = soapResponse.getSOAPPart().getContent();
            System.out.print("\nResponse SOAP Message = ");
            PluginContext.getLogger().info("\nprintSOAPResponse: Response SOAP Message = ", sourceContent.toString());
            StreamResult result = new StreamResult(System.out);
            transformer.transform(sourceContent, result);
        }
    }
}
