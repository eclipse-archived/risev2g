/*******************************************************************************
 *  Copyright (c) 2016 Dr.-Ing. Marc Mültin.
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  which accompanies this distribution, and is available at
 *  http://www.eclipse.org/legal/epl-v10.html
 *
 *  Contributors:
 *    Dr.-Ing. Marc Mültin - initial API and implementation and initial documentation
 *******************************************************************************/
package org.eclipse.risev2g.shared.exiCodec;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamResult;
import org.eclipse.risev2g.shared.enumerations.GlobalValues;
import org.openexi.proc.common.EXIOptionsException;
import org.openexi.proc.common.GrammarOptions;
import org.openexi.proc.grammars.GrammarCache;
import org.openexi.sax.EXIReader;
import org.openexi.sax.Transmogrifier;
import org.openexi.sax.TransmogrifierException;
import org.openexi.schema.EXISchema;
import org.openexi.scomp.EXISchemaFactory;
import org.openexi.scomp.EXISchemaFactoryException;
import org.openexi.scomp.EXISchemaReader;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

public final class OpenEXICodec extends ExiCodec {
	// -- BEGIN: SINGLETON DEFINITION --
	/*
	 *  Eager instantiation of the singleton, since a OpenEXICodec is always needed. 
	 *  The JVM creates the unique instance when the class is loaded and before any thread tries to 
	 *  access the instance variable -> thread safe.
	 */
	private static final OpenEXICodec instance = new OpenEXICodec();
	private Transmogrifier transmogrifier;
	private GrammarCache grammarCache;
	private EXISchemaFactory exiSchemaFactory;
	private InputStream schemaMsgDefIS;
	private InputStream schemaAppProtocolIS;
	private EXISchema exiSchemaAppProtocol;
	private EXISchema exiSchemaMsgDef;
	private short options;
	private SAXTransformerFactory saxTransformerFactory;
	private SAXParserFactory saxParserFactory;
	private TransformerHandler transformerHandler;
	private EXIReader exiReader;
	
	private OpenEXICodec() {
		super();
		
		// All EXI options can expressed in a single short integer
        setOptions(GrammarOptions.DEFAULT_OPTIONS);
        
        // The Transmogrifier performs the translation from XML to EXI format
        setTransmogrifier(new Transmogrifier());
        getTransmogrifier().setValuePartitionCapacity(0);
//        getTransmogrifier().setDivertBuiltinGrammarToAnyType(true); // enable V2G's built-in grammar usage
        
        // Standard SAX methods parse content and lexical values
        setSaxTransformerFactory((SAXTransformerFactory) SAXTransformerFactory.newInstance());
        setSaxParserFactory(SAXParserFactory.newInstance());
        getSaxParserFactory().setNamespaceAware(true);

        try {
			setTransformerHandler(getSaxTransformerFactory().newTransformerHandler());
		} catch (TransformerConfigurationException e) {
			getLogger().error(e.getClass().getSimpleName() + " occurred while trying to instantiate OpenEXICodec");
		}
        
        // EXIReader infers and reconstructs the XML file structure
        setExiReader(new EXIReader());
        getExiReader().setValuePartitionCapacity(0);
        
        setExiSchemaFactory(new EXISchemaFactory());
        EXISchemaFactoryExceptionHandler esfe = new EXISchemaFactoryExceptionHandler();
        getExiSchemaFactory().setCompilerErrorHandler(esfe);
        
        setSchemaAppProtocolIS(getClass().getResourceAsStream(GlobalValues.SCHEMA_PATH_APP_PROTOCOL.toString()));
        setSchemaMsgDefIS(getClass().getResourceAsStream(GlobalValues.SCHEMA_PATH_MSG_DEF.toString()));
	
        /*
         * It is currently a problem with OpenExi to resolve the XSDs which are imported in V2G_CI_MsgDef.xsd.
         * A workaround is the serialization of V2G_CI_MsgDef.xsd into an EXI grammar file (.exig) which can
         * be done by following tutorial 4 (http://openexi.sourceforge.net/tutorial/example4.htm). 
         * Thus, EXIficient implementation uses the XSD file while OpenEXI uses the EXIG file.
         */
        try {
			setExiSchemaAppProtocol(getExiSchemaFactory().compile(new InputSource(getSchemaAppProtocolIS())));
			setExiSchemaMsgDef(new EXISchemaReader().parse(getClass().getResourceAsStream("/schemas/V2G_CI_MsgDef.exig")));
        } catch (IOException | EXISchemaFactoryException | EXIOptionsException e) {
        	getLogger().error(e.getClass().getSimpleName() + " occurred while trying to set EXI schema", e);
		}
	}
	
	public static OpenEXICodec getInstance() {
		return instance;
	}
	// -- END: SINGLETON DEFINITION --
	
	
	@Override
	public byte[] encodeEXI(Object jaxbObject, boolean supportedAppProtocolHandshake) {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		
		try {
			/*
			 * Set the schema and EXI options in the Grammar Cache.
			 * The Grammar Cache stores the XML schema and options used to encode an EXI file. 
			 * The settings must match when encoding and subsequently decoding a data set.
			 */
			if (supportedAppProtocolHandshake)
				setGrammarCache(new GrammarCache(getExiSchemaAppProtocol(), getOptions()));
			else 
				setGrammarCache(new GrammarCache(getExiSchemaMsgDef(), getOptions()));
			
			// Set the configuration options in the Transmogrifier
			getTransmogrifier().setGrammarCache(getGrammarCache());

			// Set the output stream
			getTransmogrifier().setOutputStream(baos);
			
			// Encode the input stream
			getTransmogrifier().encode(new InputSource(marshalToInputStream(jaxbObject)));

			byte[] encodedExi = baos.toByteArray();
			baos.close();
			
			return encodedExi;
		} catch (IOException | EXIOptionsException | TransmogrifierException e) {
			getLogger().error(e.getClass().getSimpleName() + " occurred while trying to encode EXI", e);
			return null;
		} 
	}
	
	
	@Override
	public Object decodeEXI(byte[] exiEncodedMessage, boolean supportedAppProtocolHandshake) {
		StringWriter stringWriter = new StringWriter();
		
		try {
			/*
			 * Set the schema and EXI options in the Grammar Cache.
			 * The Grammar Cache stores the XML schema and options used to encode an EXI file. 
			 * The settings must match when encoding and subsequently decoding a data set.
			 */
			if (supportedAppProtocolHandshake)
				setGrammarCache(new GrammarCache(getExiSchemaAppProtocol(), getOptions()));
			else 
				setGrammarCache(new GrammarCache(getExiSchemaMsgDef(), getOptions()));

			// Use the Grammar Cache to set the schema and grammar options for EXIReader
			getExiReader().setGrammarCache(getGrammarCache());

			// Prepare to send the results from the transformer to an OutputStream object
			getTransformerHandler().setResult(new StreamResult(stringWriter));
			
			// Assign the transformer handler to interpret XML content
			getExiReader().setContentHandler(getTransformerHandler());
			
			// Parse the information from exiEncodedMessage
			getExiReader().parse(new InputSource(new ByteArrayInputStream(exiEncodedMessage)));
			
			// Get the resulting string
			setDecodedExi(stringWriter.getBuffer().toString());
			
			return unmarshallToMessage(getDecodedExi());
		} catch (IOException | EXIOptionsException | SAXException e) {
			getLogger().error(e.getClass().getSimpleName() + " occurred while trying to decode EXI", e);
			return null;
		} 
	}
	

	public Transmogrifier getTransmogrifier() {
		return transmogrifier;
	}

	public void setTransmogrifier(Transmogrifier transmogrifier) {
		this.transmogrifier = transmogrifier;
	}

	public GrammarCache getGrammarCache() {
		return grammarCache;
	}

	public void setGrammarCache(GrammarCache grammarCache) {
		this.grammarCache = grammarCache;
	}

	public EXISchemaFactory getExiSchemaFactory() {
		return exiSchemaFactory;
	}

	public void setExiSchemaFactory(EXISchemaFactory exiSchemaFactory) {
		this.exiSchemaFactory = exiSchemaFactory;
	}

	private InputStream getSchemaMsgDefIS() {
		return schemaMsgDefIS;
	}

	private void setSchemaMsgDefIS(InputStream schemaMsgDefIS) {
		this.schemaMsgDefIS = schemaMsgDefIS;
	}

	private InputStream getSchemaAppProtocolIS() {
		return schemaAppProtocolIS;
	}

	private void setSchemaAppProtocolIS(InputStream schemaAppProtocolIS) {
		this.schemaAppProtocolIS = schemaAppProtocolIS;
	}

	private EXISchema getExiSchemaAppProtocol() {
		return exiSchemaAppProtocol;
	}

	private void setExiSchemaAppProtocol(EXISchema exiSchemaAppProtocol) {
		this.exiSchemaAppProtocol = exiSchemaAppProtocol;
	}

	private short getOptions() {
		return options;
	}

	private void setOptions(short options) {
		this.options = options;
	}

	public SAXTransformerFactory getSaxTransformerFactory() {
		return saxTransformerFactory;
	}

	public void setSaxTransformerFactory(SAXTransformerFactory saxTransformerFactory) {
		this.saxTransformerFactory = saxTransformerFactory;
	}

	private SAXParserFactory getSaxParserFactory() {
		return saxParserFactory;
	}

	private void setSaxParserFactory(SAXParserFactory saxParserFactory) {
		this.saxParserFactory = saxParserFactory;
	}

	public TransformerHandler getTransformerHandler() {
		return transformerHandler;
	}

	public void setTransformerHandler(TransformerHandler transformerHandler) {
		this.transformerHandler = transformerHandler;
	}

	public EXIReader getExiReader() {
		return exiReader;
	}

	public void setExiReader(EXIReader exiReader) {
		this.exiReader = exiReader;
	}

	public EXISchema getExiSchemaMsgDef() {
		return exiSchemaMsgDef;
	}

	public void setExiSchemaMsgDef(EXISchema exiSchemaMsgDef) {
		this.exiSchemaMsgDef = exiSchemaMsgDef;
	}
}
