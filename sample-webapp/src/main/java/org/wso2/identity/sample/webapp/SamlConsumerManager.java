package org.wso2.identity.sample.webapp;

import org.apache.axiom.util.UIDGenerator;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.joda.time.DateTime;
import org.opensaml.Configuration;
import org.opensaml.DefaultBootstrap;
import org.opensaml.common.SAMLVersion;
import org.opensaml.saml2.core.*;
import org.opensaml.saml2.core.impl.*;
import org.opensaml.xml.ConfigurationException;
import org.opensaml.xml.XMLObject;
import org.opensaml.xml.io.*;
import org.opensaml.xml.util.Base64;
import org.opensaml.xml.util.XMLHelper;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.wso2.identity.sample.webapp.util.AssertionEncoder;
import org.wso2.identity.sample.webapp.util.OAuthTokenDTO;
import org.wso2.identity.sample.webapp.util.Util;
import org.xml.sax.SAXException;

import javax.servlet.ServletConfig;
import javax.servlet.http.HttpServletRequest;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

public class SamlConsumerManager {

    private static final Log log = LogFactory.getLog(SamlConsumerManager.class);

    private String consumerUrl = null;
	private String authReqRandomId = Integer.toHexString(new Double(Math.random()).intValue());
	private String relayState = null;
	private String issuerId = null;
	private String idpUrl = null;
	private String attribIndex = null;
	private String oauthTokenEndpoint = null;
	private String consumerKey = null;
	private String consumerSecret = null;
    private String apiEndPoint = null;
    private Assertion assertion;
    private OAuthTokenDTO oauthTokenDTO;
    private static String encodedAssertion;

    public static String getEncodedAssertion(){
        return encodedAssertion;
    }

    public SamlConsumerManager(ServletConfig servletConfig) throws ConfigurationException {
		
		consumerUrl = Util.getConfiguration(servletConfig, "ConsumerUrl");
		idpUrl = Util.getConfiguration(servletConfig, "IdpUrl");
		issuerId = Util.getConfiguration(servletConfig, "Issuer");
		attribIndex = Util.getConfiguration(servletConfig, "AttributeConsumingServiceIndex");
		oauthTokenEndpoint = Util.getConfiguration(servletConfig, "OAuthTokenEndPoint");
		consumerKey = Util.getConfiguration(servletConfig, "ConsumerKey");
		consumerSecret = Util.getConfiguration(servletConfig, "ConsumerSecret");
		apiEndPoint = Util.getConfiguration(servletConfig, "ApiEndPoint");

		/* Initializing the OpenSAML library, loading default configurations */
		DefaultBootstrap.bootstrap();
	}

	/**
	 * Returns the redirection URL with the appended SAML2
	 * Request message
	 * 
	 * @param request
	 * 
	 * @return redirectionUrl<dependency>
	 *         <groupId>org.opensaml</groupId>
	 *         <artifactId>opensaml</artifactId>
	 *         <version>2.2.3</version>
	 *         </dependency>
	 */
	public String buildRequestMessage(HttpServletRequest request) {

		RequestAbstractType requestMessage = null;

		// time to build the authentication request message
		if (request.getParameter("logout") == null) {
			requestMessage = buildAuthnRequestObject();

		} else { // ok, user needs to be single logged out
			requestMessage = buildLogoutRequest((String) request.getSession().getAttribute("user"));
		}

		String encodedRequestMessage = null;
		try {
			encodedRequestMessage = encodeRequestMessage(requestMessage);
		} catch (MarshallingException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}

		/* SAML2 Authentication Request is appended to IP's URL */
		return idpUrl + "?SAMLRequest=" + encodedRequestMessage + "&RelayState=" +
		       relayState;
	}

	private LogoutRequest buildLogoutRequest(String user) {

		LogoutRequest logoutReq = new LogoutRequestBuilder().buildObject();

		logoutReq.setID(Util.createID());

		DateTime issueInstant = new DateTime();
		logoutReq.setIssueInstant(issueInstant);
		logoutReq.setNotOnOrAfter(new DateTime(issueInstant.getMillis() + 5 * 60 * 1000));

		IssuerBuilder issuerBuilder = new IssuerBuilder();
		Issuer issuer = issuerBuilder.buildObject();
		issuer.setValue(issuerId);
		logoutReq.setIssuer(issuer);

		NameID nameId = new NameIDBuilder().buildObject();
		nameId.setFormat("urn:oasis:names:tc:SAML:2.0:nameid-format:entity");
		nameId.setValue(user);
		logoutReq.setNameID(nameId);

		SessionIndex sessionIndex = new SessionIndexBuilder().buildObject();
		sessionIndex.setSessionIndex(UIDGenerator.generateUID());
		logoutReq.getSessionIndexes().add(sessionIndex);

		logoutReq.setReason("Single Logout");

		return logoutReq;
	}

	private AuthnRequest buildAuthnRequestObject() {

		/* Building Issuer object */
		IssuerBuilder issuerBuilder = new IssuerBuilder();
		Issuer issuer =
		                issuerBuilder.buildObject("urn:oasis:names:tc:SAML:2.0:assertion",
		                                          "Issuer", "samlp");
		issuer.setValue(issuerId); 

		/* NameIDPolicy */
		NameIDPolicyBuilder nameIdPolicyBuilder = new NameIDPolicyBuilder();
		NameIDPolicy nameIdPolicy = nameIdPolicyBuilder.buildObject();
		nameIdPolicy.setFormat("urn:oasis:names:tc:SAML:2.0:nameid-format:persistent");
		nameIdPolicy.setSPNameQualifier("Isser");
		nameIdPolicy.setAllowCreate(new Boolean(true));

		/* AuthnContextClass */
		AuthnContextClassRefBuilder authnContextClassRefBuilder = new AuthnContextClassRefBuilder();
		AuthnContextClassRef authnContextClassRef =
		                                            authnContextClassRefBuilder.buildObject("urn:oasis:names:tc:SAML:2.0:assertion",
		                                                                                    "AuthnContextClassRef",
		                                                                                    "saml");
		authnContextClassRef.setAuthnContextClassRef("urn:oasis:names:tc:SAML:2.0:ac:classes:PasswordProtectedTransport");

		/* AuthnContex */
		RequestedAuthnContextBuilder requestedAuthnContextBuilder =
		                                                            new RequestedAuthnContextBuilder();
		RequestedAuthnContext requestedAuthnContext = requestedAuthnContextBuilder.buildObject();
		requestedAuthnContext.setComparison(AuthnContextComparisonTypeEnumeration.EXACT);
		requestedAuthnContext.getAuthnContextClassRefs().add(authnContextClassRef);

		DateTime issueInstant = new DateTime();

		/* Creation of AuthRequestObject */
		AuthnRequestBuilder authRequestBuilder = new AuthnRequestBuilder();
		AuthnRequest authRequest =
		                           authRequestBuilder.buildObject("urn:oasis:names:tc:SAML:2.0:protocol",
		                                                          "AuthnRequest", "samlp");
		authRequest.setForceAuthn(new Boolean(false));
		authRequest.setIsPassive(new Boolean(false));
		authRequest.setIssueInstant(issueInstant);
		authRequest.setProtocolBinding("urn:oasis:names:tc:SAML:2.0:bindings:HTTP-POST");
		authRequest.setAssertionConsumerServiceURL(consumerUrl);
		authRequest.setIssuer(issuer);
		authRequest.setNameIDPolicy(nameIdPolicy);
		authRequest.setRequestedAuthnContext(requestedAuthnContext);
		authRequest.setID(authReqRandomId);
		authRequest.setVersion(SAMLVersion.VERSION_20);

		/* Requesting Attributes. This Index value is registered in the IDP */
		if (attribIndex != null && !attribIndex.equals("")) {
			authRequest.setAttributeConsumingServiceIndex(Integer.parseInt(attribIndex));
		}

		return authRequest;
	}

	private String encodeRequestMessage(RequestAbstractType requestMessage)
	                                                                       throws MarshallingException,
	                                                                       IOException {

		Marshaller marshaller = Configuration.getMarshallerFactory().getMarshaller(requestMessage);
		Element authDOM = marshaller.marshall(requestMessage);

		Deflater deflater = new Deflater(Deflater.DEFLATED, true);
		ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
		DeflaterOutputStream deflaterOutputStream =
		                                            new DeflaterOutputStream(byteArrayOutputStream,
		                                                                     deflater);

		StringWriter rspWrt = new StringWriter();
		XMLHelper.writeNode(authDOM, rspWrt);
		deflaterOutputStream.write(rspWrt.toString().getBytes());
		deflaterOutputStream.close();

		/* Encoding the compressed message */
		String encodedRequestMessage =
		                               Base64.encodeBytes(byteArrayOutputStream.toByteArray(),
		                                                  Base64.DONT_BREAK_LINES);
		return URLEncoder.encode(encodedRequestMessage, "UTF-8").trim();
	}

    public static String encode(String str) {

        String encodedStr = Base64.encodeBytes(str.getBytes());
        return encodedStr;
    }

    public static String decode(String encodedStr) {

        byte[] bytes = Base64.decode(encodedStr);
        return new String(bytes);
    }

	public Map<String, String> processResponseMessage(String responseMessage) {

		XMLObject responseXmlObj = null;

		try {
            String decodedStr= decode(responseMessage);
            responseXmlObj = unmarshall(decodedStr);

		} catch (ConfigurationException e) {
			e.printStackTrace();
		} catch (ParserConfigurationException e) {
			e.printStackTrace();
		} catch (SAXException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (UnmarshallingException e) {
			e.printStackTrace();
		}

		return getResult(responseXmlObj);
	}

	private XMLObject unmarshall(String responseMessage) throws ConfigurationException,
	                                                    ParserConfigurationException, SAXException,
	                                                    IOException, UnmarshallingException {

		DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
		documentBuilderFactory.setNamespaceAware(true);
		DocumentBuilder docBuilder = documentBuilderFactory.newDocumentBuilder();

		ByteArrayInputStream is = new ByteArrayInputStream(responseMessage.getBytes());

		Document document = docBuilder.parse(is);
		Element element = document.getDocumentElement();
		UnmarshallerFactory unmarshallerFactory = Configuration.getUnmarshallerFactory();
		Unmarshaller unmarshaller = unmarshallerFactory.getUnmarshaller(element);
		return unmarshaller.unmarshall(element);

	}

	/*
	 * Process the response and returns the results
	 */
	private Map<String, String> getResult(XMLObject responseXmlObj) {

		if (responseXmlObj.getDOM().getNodeName().equals("saml2p:LogoutResponse")) {
			log.info("SAML Logout response received");
			return null;
		}

		Response response = (Response) responseXmlObj;
		log.info("SAML Response: "+ response);

		assertion = response.getAssertions().get(0);
        Map<String, String> resutls = new HashMap<String, String>();
        /*
		 * If the request has failed, the IDP shouldn't send an assertion.
		 * SSO profile spec 4.1.4.2 <Response> Usage
		 */
		if (assertion != null) {

			String subject = assertion.getSubject().getNameID().getValue();
			resutls.put("Subject", subject); // get the subject

			List<AttributeStatement> attributeStatementList = assertion.getAttributeStatements();

			if (attributeStatementList != null) {
				// we have received attributes of user
				Iterator<AttributeStatement> attribStatIter = attributeStatementList.iterator();
				while (attribStatIter.hasNext()) {
					AttributeStatement statment = attribStatIter.next();
					List<Attribute> attributesList = statment.getAttributes();
					Iterator<Attribute> attributesIter = attributesList.iterator();
					while (attributesIter.hasNext()) {
						Attribute attrib = attributesIter.next();
						Element value = attrib.getAttributeValues().get(0).getDOM();
						String attribValue = value.getTextContent();
						resutls.put(attrib.getName(), attribValue);
					}
				}
			}
		}
		return resutls;
	}

    public OAuthTokenDTO generateOAuthToken() {
        if (assertion != null) {
            AssertionEncoder encoder = new AssertionEncoder();
            OAuthTokenGenerator oAuthTokenGenerator = new OAuthTokenGenerator(oauthTokenEndpoint,consumerKey,consumerSecret);
            this.encodedAssertion = encoder.encodeWithDeflate(assertion);
            oauthTokenDTO =  oAuthTokenGenerator.getToken(encoder.encode(assertion));
        }else {
            log.error("SAML2 Assertion is empty");
            oauthTokenDTO =  new OAuthTokenDTO("error","SAML2 Assertion is empty");
        }
        return oauthTokenDTO;
    }


/*    public String doApiCall(String company) {
        APIInvoker apiInvoker = new APIInvoker(apiEndPoint,oauthTokenDTO.getAccessToken());
        return apiInvoker.getQuote(company);
    }*/

    public String doApiCall(String ep) {
        APIInvoker apiInvoker = new APIInvoker(apiEndPoint,oauthTokenDTO.getAccessToken());
        return apiInvoker.getResponse(ep);
    }


    public OAuthTokenDTO renewToken() {
        OAuthTokenGenerator oAuthTokenGenerator = new OAuthTokenGenerator(oauthTokenEndpoint,consumerKey,consumerSecret);
        oauthTokenDTO = oAuthTokenGenerator.renewToken(oauthTokenDTO.getRefreshToken());
        return oauthTokenDTO;
    }
}
