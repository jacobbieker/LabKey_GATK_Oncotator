package org.labkey.sequenceanalysis.run.analysis;

import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.sequenceanalysis.SequenceAnalysisService;
import org.labkey.api.sequenceanalysis.model.AnalysisModel;
import org.labkey.api.sequenceanalysis.model.Readset;
import org.labkey.api.sequenceanalysis.pipeline.AbstractAnalysisStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.AnalysisStep;
import org.labkey.api.sequenceanalysis.pipeline.CommandLineParam;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.sequenceanalysis.pipeline.PipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.ReferenceGenome;
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;
import org.labkey.api.sequenceanalysis.run.AbstractCommandPipelineStep;
import org.labkey.api.util.FileUtil;
import org.labkey.sequenceanalysis.run.alignment.AlignerIndexUtil;
import org.labkey.sequenceanalysis.run.util.OncotatorWrapper;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * User: bieker
 * Date: 7/27/15
 * Time: 1:52 PM
 */
public class OncotatorAnalysis extends AbstractCommandPipelineStep<OncotatorWrapper> implements AnalysisStep
{
    public OncotatorAnalysis(PipelineStepProvider provider, PipelineContext ctx)
    {
        super(provider, ctx, new OncotatorWrapper(ctx.getLogger()));
    }

    public static class Provider extends AbstractAnalysisStepProvider<OncotatorAnalysis>
    {
        public Provider()
        {
            super("OncotatorAnalysis", "Oncotator Analysis", "GATK", "This will run GATK's Oncotator on the selected data. This tool annotates information onto genomic point mutations (SNPs/SNVs) and indels.", Arrays.asList(
                    ToolParameterDescriptor.create("inputFormat", "Input Format", "Input format.  Note that MAFLITE will work for any tsv file with appropriate headers, so long as all of the required headers (or an alias) are present. This can be either VCF or MAF", "ldk-simplecombo", new JSONObject(){{
                        put("storeValues", "MAF;VCF");
                    }}, true),
                    ToolParameterDescriptor.create("outputFormat", "Output Format", "Output format. This can either be a TCGAMAF, VCF, or SIMPLE_TSV", "ldk-simplecombo", new JSONObject(){{
                        put("storeValues", "TCGAMAF;VCF;SIMPLE_TSV");
                    }}, true),
                    ToolParameterDescriptor.createCommandLineParam(CommandLineParam.createSwitch("--infer_genotypes"), "inferGenotypes", "Infer Genotypes", "Forces the output renderer to populate the output genotypes as heterozygous. This option should only be used when converting a MAFLITE to a VCF; otherwise, the option has no effect.", "checkbox", new JSONObject()
                    {{
                            put("checked", false);
                        }}, true),
                    ToolParameterDescriptor.createCommandLineParam(CommandLineParam.createSwitch("--skip-no-alt"), "skipNoAlt", "Skip No Alt", "If specified, any mutation with annotation alt_allele_seen of 'False' will not be annotated or rendered. Do not use if output format is a VCF. If alt_allele_seen annotation is missing, render the mutation.", "checkbox", new JSONObject()
                    {{
                            put("checked", false);
                        }}, true),
                    ToolParameterDescriptor.createCommandLineParam(CommandLineParam.createSwitch("--prepend"), "prepend", "Prepend", "If specified for TCGAMAF output, will put a 'i_' in front of fields that are not directly rendered in Oncotator TCGA MAFs", "checkbox", new JSONObject()
                    {{
                            put("checked", false);
                        }}, true)
            ), null, null);
        }

        @Override
        public OncotatorAnalysis create(PipelineContext ctx)
        {
            return new OncotatorAnalysis(this, ctx);
        }
    }


    @Override
    public void init(List<AnalysisModel> models) throws PipelineJobException
    {

    }

    @Override
    public Output performAnalysisPerSampleRemote(Readset rs, File inputVcf, ReferenceGenome referenceGenome, File outputDir) throws PipelineJobException
    {

        AnalysisOutputImpl output = new AnalysisOutputImpl();
        output.addInput(inputVcf, "Input VCF File");

        String outputFormat = getProvider().getParameterByName("outputFormat").extractValue(getPipelineCtx().getJob(), getProvider(), String.class, "MAF");
        String inputFormat = getProvider().getParameterByName("inputFormat").extractValue(getPipelineCtx().getJob(), getProvider(), String.class, "MAF");

        File outputFile = new File(outputDir, FileUtil.getBaseName(inputVcf) + ".annotated");
        getWrapper().setOutputDir(outputDir);

        if (inputFormat.equalsIgnoreCase("MAF"))
        {
            List<String> args = new ArrayList<>();
            args.addAll(getClientCommandArgs());
            args.add("-i");
            args.add("MAFLITE");
            args.add("-o");
            // Check what output format is requested
            if (outputFormat.equalsIgnoreCase("SIMPLE_TSV"))
            {
                args.add("SIMPLE_TSV");
            } else if (outputFormat.equalsIgnoreCase("VCF"))
            {
                args.add("VCF");
            } else
            {
                args.add("TCGAMAF");
            }

            getWrapper().execute(inputVcf, outputFile, args);
        }
        else if (inputFormat.equalsIgnoreCase("VCF"))
        {

            List<String> args = new ArrayList<>();
            args.addAll(getClientCommandArgs());
            args.add("-i");
            args.add("VCF");
            args.add("-o");
            // Check what output format is requested
            if (outputFormat.equalsIgnoreCase("SIMPLE_TSV"))
            {
                args.add("SIMPLE_TSV");
            } else if (outputFormat.equalsIgnoreCase("VCF"))
            {
                args.add("VCF");
            } else
            {

                args.add("TCGAMAF");
            }

            getWrapper().execute(inputVcf, outputFile, args);
        }
        else
        {
            System.out.println("Error: Not a valid input format");
        }

        output.addOutput(outputFile, "VCF File");

        return output;
    }

    @Override
    public Output performAnalysisPerSampleLocal(AnalysisModel model, File inputVcf, File referenceFasta) throws PipelineJobException
    {
        //perform a check to see if the reference files have been downloaded to the genome dir
        File genomeDir = SequenceAnalysisService.get().getReferenceGenome(model.getReferenceLibrary(), getPipelineCtx().getJob().getUser()).getSourceFastaFile().getParentFile();
        File cachedDir = new File(genomeDir, AlignerIndexUtil.INDEX_DIR + "/oncotator");

        return null;
    }
}
