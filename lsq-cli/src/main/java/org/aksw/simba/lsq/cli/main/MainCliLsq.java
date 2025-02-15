package org.aksw.simba.lsq.cli.main;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.aksw.commons.io.syscall.sort.SysSort;
import org.aksw.commons.io.util.StdIo;
import org.aksw.commons.io.util.UriToPathUtils;
import org.aksw.commons.util.exception.ExceptionUtilsAksw;
import org.aksw.jena_sparql_api.conjure.datapod.api.RdfDataPod;
import org.aksw.jena_sparql_api.conjure.datapod.impl.DataPods;
import org.aksw.jena_sparql_api.conjure.dataref.rdf.api.DataRefSparqlEndpoint;
import org.aksw.jena_sparql_api.core.connection.SparqlQueryConnectionWithReconnect;
import org.aksw.jena_sparql_api.mapper.hashid.HashIdCxt;
import org.aksw.jena_sparql_api.mapper.proxy.MapperProxyUtils;
import org.aksw.jena_sparql_api.rx.DatasetFactoryEx;
import org.aksw.jena_sparql_api.rx.DatasetGraphFactoryEx;
import org.aksw.jena_sparql_api.rx.RDFDataMgrEx;
import org.aksw.jena_sparql_api.rx.RDFDataMgrRx;
import org.aksw.jena_sparql_api.rx.SparqlRx;
import org.aksw.jena_sparql_api.rx.SparqlScriptProcessor;
import org.aksw.jena_sparql_api.rx.dataset.DatasetFlowOps;
import org.aksw.jena_sparql_api.rx.dataset.ResourceInDatasetFlowOps;
import org.aksw.jena_sparql_api.rx.io.resultset.NamedGraphStreamCliUtils;
import org.aksw.jena_sparql_api.stmt.SparqlStmt;
import org.aksw.jena_sparql_api.utils.dataset.GroupedResourceInDataset;
import org.aksw.jena_sparql_api.utils.model.ResourceInDataset;
import org.aksw.simba.lsq.cli.main.cmd.CmdLsqAnalyze;
import org.aksw.simba.lsq.cli.main.cmd.CmdLsqBenchmarkCreate;
import org.aksw.simba.lsq.cli.main.cmd.CmdLsqBenchmarkPrepare;
import org.aksw.simba.lsq.cli.main.cmd.CmdLsqBenchmarkRun;
import org.aksw.simba.lsq.cli.main.cmd.CmdLsqInvert;
import org.aksw.simba.lsq.cli.main.cmd.CmdLsqMain;
import org.aksw.simba.lsq.cli.main.cmd.CmdLsqProbe;
import org.aksw.simba.lsq.cli.main.cmd.CmdLsqRdfize;
import org.aksw.simba.lsq.cli.main.cmd.CmdLsqRehash;
import org.aksw.simba.lsq.core.LsqBenchmarkProcessor;
import org.aksw.simba.lsq.core.LsqEnrichments;
import org.aksw.simba.lsq.core.LsqProcessor;
import org.aksw.simba.lsq.core.LsqUtils;
import org.aksw.simba.lsq.core.ResourceParser;
import org.aksw.simba.lsq.core.Skolemize;
import org.aksw.simba.lsq.model.ExperimentConfig;
import org.aksw.simba.lsq.model.ExperimentRun;
import org.aksw.simba.lsq.model.LsqQuery;
import org.aksw.simba.lsq.util.NestedResource;
import org.aksw.simba.lsq.vocab.LSQ;
import org.apache.commons.io.output.CloseShieldOutputStream;
import org.apache.jena.datatypes.xsd.XSDDateTime;
import org.apache.jena.ext.com.google.common.hash.Hashing;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdfconnection.RDFConnection;
import org.apache.jena.rdfconnection.RDFConnectionFactory;
import org.apache.jena.rdfconnection.RDFConnectionRemote;
import org.apache.jena.rdfconnection.SparqlQueryConnection;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFFormat;
import org.apache.jena.riot.WebContent;
import org.apache.jena.shared.PrefixMapping;
import org.apache.jena.sparql.lang.arq.ParseException;
import org.apache.jena.tdb2.TDB2Factory;
import org.apache.jena.util.ResourceUtils;
import org.apache.jena.vocabulary.DCTerms;
import org.apache.jena.vocabulary.XSD;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.topbraid.spin.vocabulary.SP;

import com.google.common.io.BaseEncoding;

import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.FlowableTransformer;
import picocli.CommandLine;



/**
 * This is the main class of Linked Sparql Queries
 *
 * @author raven
 *
 */
// @SpringBootApplication
public class MainCliLsq {

    private static final Logger logger = LoggerFactory.getLogger(MainCliLsq.class);

/*
 * Spring Boot Code - in case we need it at some point; for now it turned out to not bring any benefit
 */

//    public static void main(String[] args) {
//        try (ConfigurableApplicationContext ctx = new SpringApplicationBuilder()
//                .sources(ConfigCliLsq.class)
//            .bannerMode(Banner.Mode.OFF)
//            // If true, Desktop.isDesktopSupported() will return false, meaning we can't
//            // launch a browser
//            .headless(false).web(WebApplicationType.NONE).run(args)) {
//        }
//    }

//    @Configuration
//    @PropertySource("classpath:lsq-core.properties")
//    public static class ConfigCliLsq {
//        @Value("lsq-core.version")
//        protected String lsqCoreVersion;
//
//        @Bean
//        public ApplicationRunner applicationRunner() {
//            return args -> {
//                try {
//                    int exitCode = mainCore(args.getSourceArgs());
//                    System.exit(exitCode);
//
//                } catch(Exception e) {
//                    ExceptionUtils.rethrowIfNotBrokenPipe(e);
//                }
//            };
//        }
//    }

    public static void main(String[] args) {
        int exitCode = mainCore(args);
        System.exit(exitCode);
    }

    public static int mainCore(String[] args) {
        return new CommandLine(new CmdLsqMain())
            .setExecutionExceptionHandler((ex, commandLine, parseResult) -> {
                ExceptionUtilsAksw.rethrowIfNotBrokenPipe(ex);
                return 0;
            })
            .execute(args);
    }


    public static Flowable<ResourceInDataset> createLsqRdfFlow(CmdLsqRdfize rdfizeCmd) throws FileNotFoundException, IOException, ParseException {
        String logFormat = rdfizeCmd.inputLogFormat;
        List<String> logSources = rdfizeCmd.nonOptionArgs;
        String baseIri = rdfizeCmd.baseIri;

        String tmpHostHashSalt = rdfizeCmd.hostSalt;
        if(tmpHostHashSalt == null) {
            tmpHostHashSalt = UUID.randomUUID().toString();
            // TODO Make host hashing a post processing step for the log rdfization
            logger.info("Auto generated host hash salt (only used for non-rdf log input): " + tmpHostHashSalt);
        }

        String hostHashSalt = tmpHostHashSalt;

        String endpointUrl = rdfizeCmd.endpointUrl;
// TODO Validate all sources first: For trig files it is ok if no endpoint is specified
//		if(endpointUrl == null) {
//			throw new RuntimeException("Please specify the URL of the endpoint the provided query logs are assigned to.");
//		}


        List<String> rawPrefixSources = rdfizeCmd.prefixSources;
        Iterable<String> prefixSources = LsqUtils.prependDefaultPrefixSources(rawPrefixSources);
        Function<String, SparqlStmt> sparqlStmtParser = LsqUtils.createSparqlParser(prefixSources);


        Map<String, ResourceParser> logFmtRegistry = LsqUtils.createDefaultLogFmtRegistry();


        // Hash function which is applied after combining host names with salts
        Function<String, String> hashFn = str -> BaseEncoding.base64Url().omitPadding().encode(Hashing.sha256()
                .hashString(str, StandardCharsets.UTF_8)
                .asBytes());

        Flowable<ResourceInDataset> logRdfEvents = Flowable
            .fromIterable(logSources)
            .flatMap(logSource -> {
                Flowable<ResourceInDataset> st = LsqUtils.createReader(
                        logSource,
                        sparqlStmtParser,
                        logFormat,
                        logFmtRegistry,
                        baseIri,
                        hostHashSalt,
                        endpointUrl,
                        hashFn);

                return st;
            });


        if(rdfizeCmd.slimMode) {
//            CmdNgsMap cmd = new CmdNgsMap();
//            cmd.mapSpec = new MapSpec();
//            cmd.mapSpec.stmts.add("lsq-slimify.sparql");

            SparqlScriptProcessor sparqlProcessor = SparqlScriptProcessor.createWithEnvSubstitution(null);
            sparqlProcessor.process("lsq-slimify.sparql");
            List<SparqlStmt> sparqlStmts = sparqlProcessor.getSparqlStmts().stream()
                    .map(Entry::getKey).collect(Collectors.toList());


//			cmd.nonOptionArgs.addAll(cmdInvert.nonOptionArgs);

//			JenaSystem.init();
            //RDFDataMgrEx.loadQueries("lsq-slimify.sparql", PrefixMapping.Extended);

//			SparqlStmtUtils.processFile(pm, "lsq-slimify.sparql");
//			MainCliNamedGraphStream.createMapper2(); //map(DefaultPrefixes.prefixes, cmd);
            // PrefixMapping.Extended
            FlowableTransformer<ResourceInDataset, ResourceInDataset> mapper =
                    DatasetFlowOps.createMapperDataset(sparqlStmts,
                            r -> r.getDataset(),
                            (r, ds) -> r.inDataset(ds),
                            DatasetGraphFactoryEx::createInsertOrderPreservingDatasetGraph,
                            cxt -> {});


            logRdfEvents = logRdfEvents
                    .compose(mapper);
        }

        if(!rdfizeCmd.noMerge) {
            SysSort sortCmd = new SysSort();
            sortCmd.bufferSize = rdfizeCmd.bufferSize;
            sortCmd.temporaryDirectory = rdfizeCmd.temporaryDirectory;

            FlowableTransformer<GroupedResourceInDataset, GroupedResourceInDataset> sorter = ResourceInDatasetFlowOps.createSystemSorter(sortCmd, null);
            logRdfEvents = logRdfEvents
                    .compose(ResourceInDatasetFlowOps.groupedResourceInDataset())
                    .compose(sorter)
                    .compose(ResourceInDatasetFlowOps::mergeConsecutiveResourceInDatasets)
                    .flatMap(ResourceInDatasetFlowOps::ungrouperResourceInDataset);
        }

        return logRdfEvents;
    }

    public static void rdfize(CmdLsqRdfize cmdRdfize) throws Exception {
        Flowable<ResourceInDataset> logRdfEvents = createLsqRdfFlow(cmdRdfize);
        try {
            RDFDataMgrRx.writeResources(logRdfEvents, StdIo.openStdOutWithCloseShield(), RDFFormat.TRIG_BLOCKS);
            logger.info("RDFization completed successfully");
        } catch(Exception e) {
            ExceptionUtilsAksw.rethrowIfNotBrokenPipe(e);
        }
    }

    public static void probe(CmdLsqProbe cmdProbe) {
        List<String> nonOptionArgs = cmdProbe.nonOptionArgs;
        if(nonOptionArgs.isEmpty()) {
            logger.error("No arguments provided.");
            logger.error("Argument must be one or more log files which will be probed against all registered LSQ log formats");
        }

        for(int i = 0; i < nonOptionArgs.size(); ++i) {
            String filename = nonOptionArgs.get(i);

            List<Entry<String, Number>> bestCands = LsqUtils.probeLogFormat(filename);

            System.out.println(filename + "\t" + bestCands);
        }
    }

    @Deprecated // The process was changed that inversion of the
    // log-record-to-query relation is no longer needed as it is now how the process works
    public static void invert(CmdLsqInvert cmdInvert) throws Exception {
        //CmdNgsMap cmd = new CmdNgsMap();
//        SparqlScriptProcessor sparqlProcessor = SparqlScriptProcessor.createWithEnvSubstitution(null);
//        sparqlProcessor.process("lsq-invert-rdfized-log.sparql");
        // cmd.mapSpec = new MapSpec();
        // cmd.mapSpec.stmts.add("lsq-invert-rdfized-log.sparql");
        // sparqlProcessor.process(cmdInvert.nonOptionArgs) ;
        // cmd.nonOptionArgs.addAll(cmdInvert.nonOptionArgs);

        // JenaSystem.init();

        NamedGraphStreamCliUtils.execMap(null,
                cmdInvert.nonOptionArgs,
                Arrays.asList(Lang.TRIG, Lang.NQUADS),
                Arrays.asList("lsq-invert-rdfized-log.sparql"),
                null,
                null,
                20);


//        Flowable<Dataset> flow = NamedGraphStreamCliUtils.createNamedGraphStreamFromArgs(cmdInvert.nonOptionArgs, null, null, Arrays.asList(Lang.TRIG, Lang.NQUADS));
//
//        try (OutputStream out = StdIo.openStdOutWithCloseShield()) {
//	        // StreamRdf streamRdf = StreamRDFWriter.getWriterStream(out, RDFFormat.TRIG_PRETTY, null);
//
//	        // NamedGraphStreamOps.map(DefasultPrefixes.prefixes, sparqlProcessor.getSparqlStmts(), StdIo.openStdout());
//	        try (SPARQLResultExProcessor resultProcessor = SPARQLResultExProcessorBuilder.createForQuadOutput().build()) {
//	        	SparqlStmtUtils.execAny(null, null)
//
//	        }
//        }


        // SparqlStmtUtils
        // Function<RDFConnection, SPARQLResultEx> mapper = SparqlMappers.createMapperFromDataset(outputMode, stmts, resultProcessor);

    }

    public static void analyze(CmdLsqAnalyze analyzeCmd) throws Exception {
        CmdLsqRdfize rdfizeCmd = new CmdLsqRdfize();
        rdfizeCmd.nonOptionArgs = analyzeCmd.nonOptionArgs;
        rdfizeCmd.noMerge = true;
        // TODO How to obtain the baseIRI? A simple hack would be to 'grep'
        // for the id part before lsqQuery
        // The only 'clean' option would be to make the baseIri an attribute of every lsqQuery resource
        // which might be somewhat overkill

        Flowable<ResourceInDataset> flow = createLsqRdfFlow(rdfizeCmd);

        Flowable<Dataset> dsFlow = flow.map(rid -> {
            LsqQuery q = rid.as(LsqQuery.class);
            LsqEnrichments.enrichWithFullSpinModelCore(q);
            LsqEnrichments.enrichWithStaticAnalysis(q);
            // TODO createLsqRdfFlow already performs skolemize; duplicated effort
            LsqUtils.skolemize(rid, rdfizeCmd.baseIri, LsqQuery.class);
            return rid;
        })
        .map(ResourceInDataset::getDataset);

        RDFDataMgrRx.writeDatasets(dsFlow, StdIo.openStdOutWithCloseShield(), RDFFormat.TRIG_BLOCKS);
    }


    @Deprecated
    public static void rdfizeStructuralFeatures(LsqQuery q) {
        NestedResource queryRes = NestedResource.from(q);
        Function<String, NestedResource> queryAspectFn =
                aspect -> queryRes.nest("-" + aspect);

        String queryStr = q.getText();
        if(q.getParseError() == null) {
            Query query = QueryFactory.create(queryStr);
            //SparqlQueryParser parser = SparqlQueryParserImpl.create();

            LsqProcessor.rdfizeQueryStructuralFeatures(q, queryAspectFn, query);

            // Post processing: Remove skolem identifiers
            q.getModel().removeAll(null, Skolemize.skolemId, null);
        }
    }


    /*
    public static DatasetGraph replace(DatasetGraph datasetGraph, String target, String replacement) {
        DatasetGraph result;
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            StreamRDF streamRDF = StreamRDFWriterEx.getWriterStream(
                    baos,
                    RDFFormat.NTRIPLES);

            StreamRDFOps.sendDatasetToStream(datasetGraph, streamRDF);

            String ntriples = new String(baos.toByteArray());
            ntriples.replace(target, replacement);

            result = DatasetGraphFactoryEx.createInsertOrderPreservingDatasetGraph();
            try (InputStream in = new ByteArrayInputStream(ntriples.getBytes(StandardCharsets.UTF_8))) {
                RDFDataMgr.read(result, in, Lang.NTRIPLES);
            }
        }
        return result;
    }
    */



    public static void rehash(CmdLsqRehash rehashCmd) throws Exception {
        CmdLsqRdfize rdfizeCmd = new CmdLsqRdfize();
        rdfizeCmd.nonOptionArgs = rehashCmd.nonOptionArgs;
        rdfizeCmd.noMerge = true;
        // TODO How to obtain the baseIRI? A simple hack would be to 'grep'
        // for the id part before lsqQuery
        // The only 'clean' option would be to make the baseIri an attribute of every lsqQuery resource
        // which might be somewhat overkill

//        PrefixMapping pm = new PrefixMappingImpl();
//        addLsqPrefixes(pm);

        // Flowable<ResourceInDataset> flow = createLsqRdfFlow(rdfizeCmd);
        Flowable<Dataset> flow = NamedGraphStreamCliUtils.createNamedGraphStreamFromArgs(
                rehashCmd.nonOptionArgs, null, null, RDFDataMgrEx.DEFAULT_PROBE_LANGS);


        Flowable<Dataset> dsFlow = flow
                .flatMap(ResourceInDatasetFlowOps::naturalResources)
                .map(LsqUtils::rehashQueryHash)
                .map(ResourceInDataset::getDataset);


        RDFDataMgrRx.writeDatasets(dsFlow, StdIo.openStdOutWithCloseShield(), RDFFormat.TRIG_BLOCKS);
    }


    public static void addLsqPrefixes(PrefixMapping model) {
        model
            .setNsPrefix("lsqo", LSQ.NS)
            .setNsPrefix("dct", DCTerms.NS)
            .setNsPrefix("xsd", XSD.NS)
            .setNsPrefix("lsqr", "http://lsq.aksw.org/")
            .setNsPrefix("sp", SP.NS);
    }


    /**
     * Creates a model with the configuration of the benchmark experiment
     * which serves as the base for running the benchmark
     *
     *
     * @param benchmarkCreateCmd
     * @throws Exception
     */
    public static void benchmarkCreate(CmdLsqBenchmarkCreate benchmarkCreateCmd) throws Exception {

        String baseIri = benchmarkCreateCmd.baseIri;
        String endpointUrl = benchmarkCreateCmd.endpoint;

        // We could have datasetId or a datasetIri
        String datasetId = benchmarkCreateCmd.dataset;

        String datasetIri = null;
        if(datasetId != null && datasetId.matches("^[\\w]+://.*")) {
            datasetIri = datasetId;
        }

        // experimentId = distributionId + "_" + timestamp
        String datasetLabel = UriToPathUtils.resolvePath(datasetId).toString()
                .replace('/', '-');


        Instant now = Instant.now();
        ZonedDateTime zdt = ZonedDateTime.ofInstant(now, ZoneId.systemDefault());
        // Calendar nowCal = GregorianCalendar.from(zdt);
        //String timestamp = now.toString();
        String timestamp = DateTimeFormatter.ISO_LOCAL_DATE.format(zdt);



        String expId = "xc-" + datasetLabel + "_" + timestamp;

        // Not used if stdout flag is set
        String outFilename = sanitizeFilename(expId) + ".conf.ttl";


        String expIri = baseIri + expId;

        Long qt = benchmarkCreateCmd.queryTimeoutInMs;
        Long ct = benchmarkCreateCmd.connectionTimeoutInMs;

        Long datasetSize = benchmarkCreateCmd.datasetSize;

        if(datasetSize == null) {
//            logger.info("Dataset size not set. Attempting to query its size.");
//            logger.info("If successful, restart lsq with -s <obtainedDatasetSize>");

            // TODO inject default graphs
            String countQueryStr = "SELECT (COUNT(*) AS ?c) { ?s ?p ?o }";
            logger.info("Attempting to count number of triples using query " + countQueryStr);
            try(RDFConnection conn = RDFConnectionRemote.create()
                .destination(endpointUrl)
                .acceptHeaderSelectQuery(WebContent.contentTypeResultsXML)
                .build()) {

                Object raw = SparqlRx.execSelect(conn, countQueryStr)
                    .blockingFirst()
                    .get("c").asNode().getLiteralValue();
                datasetSize = ((Number)raw).longValue();
            }
        }

        Model model = DatasetFactoryEx.createInsertOrderPreservingDataset().getDefaultModel();
        addLsqPrefixes(model);

        DataRefSparqlEndpoint dataRef = model.createResource().as(DataRefSparqlEndpoint.class)
                .setServiceUrl(endpointUrl)
                .mutateDefaultGraphs(dgs -> dgs.addAll(benchmarkCreateCmd.defaultGraphs));

        ExperimentConfig cfg = model.createResource(expIri).as(ExperimentConfig.class);
        cfg
            .setIdentifier(expId)
            // .setCreationDate(nowCal)
            .setDataRef(dataRef)
            .setExecutionTimeoutForRetrieval(qt == null ? new BigDecimal(300) : new BigDecimal(qt).divide(new BigDecimal(1000)))
            .setConnectionTimeoutForRetrieval(ct == null ? new BigDecimal(60) : new BigDecimal(ct).divide(new BigDecimal(1000)))
            .setMaxResultCountForCounting(1000000l) // 1M
            .setMaxByteSizeForCounting(-1l) // limit only by count
            .setMaxResultCountForSerialization(-1l) // limit by byte size
            .setMaxByteSizeForSerialization(1000000l) // 1MB
            .setExecutionTimeoutForCounting(qt == null ? new BigDecimal(300) : new BigDecimal(qt).divide(new BigDecimal(1000)))
            .setConnectionTimeoutForCounting(ct == null ? new BigDecimal(60) : new BigDecimal(ct).divide(new BigDecimal(1000)))
            .setMaxCount(1000000000l)
            .setMaxCountAffectsTp(false)
            .setUserAgent(benchmarkCreateCmd.userAgent)
            .benchmarkSecondaryQueries(true)
            .setDatasetSize(datasetSize)
            .setDatasetLabel(datasetLabel)
            .setDatasetIri(datasetIri)
            .setBaseIri(baseIri)
            ;

        HashIdCxt tmp = MapperProxyUtils.getHashId(dataRef);
        ResourceUtils.renameResource(dataRef, baseIri + tmp.getStringId(dataRef));


        Path outPath = null;
        if (!benchmarkCreateCmd.stdout) {
            outPath = Paths.get(outFilename).toAbsolutePath().normalize();
            logger.info("Writing config to " + outPath);
        }

        try(OutputStream out = outPath == null
                ? new CloseShieldOutputStream(StdIo.openStdOutWithCloseShield())
                : Files.newOutputStream(outPath, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            RDFDataMgr.write(out, model, RDFFormat.TURTLE_BLOCKS);
        }

        if(outPath != null) {
            System.out.println(outPath);
        }
    }

    /**
     * Replaces any non-word character (with the exception of '-') with an underescore.
     * Only suitable for ASCII names; otherwise the result will be mostly composed of underscores!
     *
     * @param inputName
     * @return
     */
    public static String sanitizeFilename(String inputName) {
        String result = inputName.replaceAll("[^\\w-]", "_");
        return result;
    }

    public static void benchmarkExecute(CmdLsqBenchmarkRun benchmarkExecuteCmd) throws Exception {
        // TTODO Find a nicer way to pass config options around; reusing the command object is far from optimal
        CmdLsqRdfize rdfizeCmd = new CmdLsqRdfize();
        rdfizeCmd.nonOptionArgs = benchmarkExecuteCmd.logSources;
        rdfizeCmd.noMerge = true;

        Flowable<LsqQuery> queryFlow = createLsqRdfFlow(rdfizeCmd)
//        		.map(r -> {
//        			Resource x = ModelFactory.createDefaultModel().createResource();
//        			 x.getModel().add(r.getModel());
//        			 return x;
//        		})
                .map(r -> r.as(LsqQuery.class));


        String configSrc = benchmarkExecuteCmd.config;

        //List<String> logSources = benchmarkCmd.logSources;
        // Load the benchmark config and create a benchmark run for it

        ExperimentRun run = tryLoadRun(configSrc)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Could not detect a resource with " + LSQ.Strs.config + " property in " + configSrc));

        ExperimentConfig cfg = run.getConfig();

        String lsqBaseIri = Objects.requireNonNull(cfg.getBaseIri(), "Base IRI (e.g. http://lsq.aksw.org/) not provided");
        //LsqBenchmeclipse-javadoc:%E2%98%82=jena-sparql-api-conjure/src%5C/main%5C/java%3Corg.aksw.jena_sparql_apiarkProcessor.createProcessor()


        String runId = run.getIdentifier();
        Objects.requireNonNull(runId, "Experiment run identifier must not be null");

        // Create a folder with the database for the run
        String fsSafeId = sanitizeFilename(runId);

        Path tdb2BasePath = benchmarkExecuteCmd.tdb2BasePath;
        Path tdb2FullPath = tdb2BasePath.resolve("lsq-" + fsSafeId);
        Files.createDirectories(tdb2FullPath);
        String fullPathStr = tdb2FullPath.toString();

        logger.info("TDB2 benchmark db location: " + tdb2FullPath);

        Dataset dataset = TDB2Factory.connectDataset(fullPathStr);
        try(RDFConnection indexConn = RDFConnectionFactory.connect(dataset)) {

            DataRefSparqlEndpoint dataRef = cfg.getDataRef();
            try(RdfDataPod dataPod = DataPods.fromDataRef(dataRef)) {

                try(SparqlQueryConnection benchmarkConn =
                        SparqlQueryConnectionWithReconnect.create(() -> dataPod.openConnection())) {
                    LsqBenchmarkProcessor.process(queryFlow, lsqBaseIri, cfg, run, benchmarkConn, indexConn);
                }
            }

        } finally {
            dataset.close();
        }

        logger.info("Benchmark run " + runId + " completed successfully");
    }

    public static <T extends RDFNode> Optional<T> tryLoadResourceWithProperty(String src, Property p, Class<T> clazz) {
        Model configModel = RDFDataMgr.loadModel(src);

        Optional<T> result = configModel.listResourcesWithProperty(p)
                .nextOptional()
                .map(x -> x.as(clazz));

        return result;
    }

    public static Optional<ExperimentConfig> tryLoadConfig(String src) {
        return tryLoadResourceWithProperty(src, LSQ.endpoint, ExperimentConfig.class);
    }

    public static Optional<ExperimentRun> tryLoadRun(String src) {
        return tryLoadResourceWithProperty(src, LSQ.config, ExperimentRun.class);
    }


    public static void benchmarkPrepare(CmdLsqBenchmarkPrepare benchmarkCmd) throws Exception {
//        CmdLsqRdfize rdfizeCmd = new CmdLsqRdfize();
//        rdfizeCmd.nonOptionArgs = benchmarkCmd.logSources;
//        rdfizeCmd.noMerge = true;

        String configSrc = benchmarkCmd.config;

        //List<String> logSources = benchmarkCmd.logSources;
        // Load the benchmark config and create a benchmark run for it

        // The config model is first loaded with the config file
        // and subsequently enriched with a benchmark run resource

        Model configModel = DatasetFactoryEx.createInsertOrderPreservingDataset().getDefaultModel();
        RDFDataMgr.read(configModel, configSrc);

        ExperimentConfig config = configModel.listResourcesWithProperty(LSQ.endpoint)
                .nextOptional()
                .map(x -> x.as(ExperimentConfig.class))
                .orElse(null);

        // Create an instance of the config at the current time
        Instant benchmarkRunStartTimestamp = Instant.now(); //;Instant.ofEpochMilli(0);
        ZonedDateTime zdt = ZonedDateTime.ofInstant(benchmarkRunStartTimestamp, ZoneId.systemDefault());
        Calendar cal = GregorianCalendar.from(zdt);
        XSDDateTime xsddt = new XSDDateTime(cal);
        String timestampStr = DateTimeFormatter.ISO_INSTANT.format(zdt);

        String configId = config.getIdentifier();
        String runId = configId + "_" + timestampStr;

        // Not used if stdout flag is set
        String outFilename = sanitizeFilename(runId) + ".run.ttl";

        ExperimentRun expRun = configModel
                .createResource()
                .as(ExperimentRun.class)
                .setConfig(config)
                .setTimestamp(xsddt)
                .setIdentifier(runId);


        String runIri = config.getBaseIri() + runId;
        ResourceUtils.renameResource(expRun, runIri);


        Path outPath = null;
        if (!benchmarkCmd.stdout) {
            outPath = Paths.get(outFilename).toAbsolutePath().normalize();
            logger.info("Writing run information to " + outPath);
        }

        try (OutputStream out = outPath == null
                ? StdIo.openStdOutWithCloseShield()
                : Files.newOutputStream(outPath, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            RDFDataMgr.write(out, configModel, RDFFormat.TURTLE_BLOCKS);
        }

        if(outPath != null) {
            System.out.println(outPath);
        }
    }

}


//for(LsqQuery item : queryFlow.blockingIterable()) {
//Set<LocalExecution> test = item.getLocalExecutions(LocalExecution.class);
//LocalExecution r = item.getModel().createResource().as(LocalExecution.class);
//test.add(r);
//Model m = item.getModel();
////System.out.println(m.getClass());
////System.out.println(m.getGraph().getClass());
//System.out.println(test.size());
//r.inModel(m);
//test.clear();
//System.out.println(test.size());
//System.exit(0);
//}


//
//String endpoint = benchmarkCmd.endpoint;
//
//LsqProcessor processor = createLsqProcessor(benchmarkCmd);
//
//String datasetIri = benchmarkCmd.dataset;
//String serviceUrl = benchmarkCmd.endpoint;
//

//@Deprecated
//public static LsqProcessor createLsqProcessor(ExperimentConfig cfg) throws FileNotFoundException, IOException, ParseException {
//
    // The experiment IRI is distributionLabel + current timestamp
    // Note, that distribution + timestamp nails down the hardware resources used to run the experiment
//    String distributionIri = benchmarkCmd.dataset;
//    String distributionId = UriToPathUtils.resolvePath(distributionIri).toString()
//            .replace('/', '-');
//
//    String timestamp = Instant.now().toString();
//
//    String defaultExperimentId = "x-" + distributionId + "_" + timestamp;
//    String defaultExperimentIri = benchmarkCmd.baseIri + defaultExperimentId;
//
//    DataRefSparqlEndpoint se = Objects.requireNonNull(cfg.getDataRef());
//
//
//
//    LsqConfigImpl config = new LsqConfigImpl();
//    config
//        .setPrefixSources(Collections.emptyList())
//        .setHttpUserAgent(cfg.getUserAgent())
//        .setDatasetSize(cfg.getDatasetSize())
//        .setBenchmarkEndpoint(se.getServiceUrl())
//        .addBenchmarkDefaultGraphs(se.getDefaultGraphs())
//        .setBenchmarkQueryExecutionTimeoutInMs(cfg.getQueryTimeout() == null ? null : cfg.getQueryTimeout().multiply(new BigDecimal(1000)).longValue())
//        .setRdfizerQueryExecutionEnabled(true)
//        .setDelayInMs(cfg.getRequestDelay() == null ? null : cfg.getRequestDelay().multiply(new BigDecimal(1000)).longValue())
//        .setExperimentId(cfg.getIdentifier())
//        .setExperimentIri(cfg.getURI())
//        .setDatasetLabel(cfg.getDatasetLabel())
//        ;
//
//    LsqProcessor result = LsqUtils.createProcessor(config);
//    return result;
//}



//public static void mainOld(String[] args) throws Exception {
//
////	Flowable.fromIterable(LongStream.iterate(0, i -> i + 1)::iterator)
////	.forEach(x -> {
////		System.out.println(x);
////		throw new IOException("x");
////	});
//
//  CmdLsqMain cmdMain = new CmdLsqMain();
//
//  CmdLsqProbe probeCmd = new  CmdLsqProbe();
//  CmdLsqRdfize rdfizeCmd = new  CmdLsqRdfize();
////	CmdLsqInvert invertCmd = new  CmdLsqInvert();
//  CmdLsqBenchmarkMain benchmarkCmd = new  CmdLsqBenchmarkMain();
//  CmdLsqAnalyze analyzeCmd = new  CmdLsqAnalyze();
//
//  JCommander jc = JCommander.newBuilder()
//          .addObject(cmdMain)
//          .addCommand("probe", probeCmd)
//          .addCommand("rdfize", rdfizeCmd)
////			.addCommand("invert", invertCmd)
//          .addCommand("analyze", analyzeCmd)
//          .addCommand("benchmark", benchmarkCmd)
//          .build();
//
//  JCommander benchmarkSubCmds = jc.getCommands().get("benchmark");
//  CmdLsqBenchmarkCreate benchmarkCreateCmd = new CmdLsqBenchmarkCreate();
//  benchmarkSubCmds.addCommand("create", benchmarkCreateCmd);
//  CmdLsqBenchmarkPrepare benchmarkRunCmd = new CmdLsqBenchmarkPrepare();
//  benchmarkSubCmds.addCommand("run", benchmarkRunCmd);
//
//
//  jc.parse(args);
//
//  if (cmdMain.help || jc.getParsedCommand() == null) {
//      jc.usage();
//      return;
//  }
//
//  // TODO Change this to a plugin system - for now I hack this in statically
//  String cmd = jc.getParsedCommand();
//  switch (cmd) {
//  case "probe": {
//      probe(probeCmd);
//      break;
//  }
//  case "rdfize": {
//      rdfize(rdfizeCmd);
//      break;
//  }
////	case "invert": {
////		invert(invertCmd);
////		break;
////	}
//  case "analyze": {
//      analyze(analyzeCmd);
//      break;
//  }
//  case "benchmark": {
//      String benchmarkSubCmd = benchmarkSubCmds.getParsedCommand();
//      switch(benchmarkSubCmd) {
//      case "create":
//          benchmarkCreate(benchmarkCreateCmd);
//          break;
//      case "run":
//          benchmarkPrepare(benchmarkRunCmd);
//          break;
//      default:
//          throw new RuntimeException("Unsupported command: " + cmd);
//      }
//      break;
//  }
//  default:
//      throw new RuntimeException("Unsupported command: " + cmd);
//  }
//}


//QueryExecutionFactory qef = FluentQueryExecutionFactory.http("http://dbpedia.org/sparql")
//.config()
//  .withPagination(10000)
//.end()
//.create();
//try(QueryExecution qe = qef.createQueryExecution("SELECT * { ?s ?p ?o } LIMIT 10001")) {
//System.out.println("#items seen: " + ResultSetFormatter.consume(qe.execSelect()));
//}
//System.exit(0);
