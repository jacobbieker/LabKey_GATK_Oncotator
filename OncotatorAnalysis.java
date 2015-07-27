/*
 * Copyright (C) 2015.  Jacob Bieker, jacob@bieker.us, ww.jacobbieker.com
 *
 *                                This program is free software; you can redistribute it and/or modify
 *                                it under the terms of the GNU General Public License as published by
 *                                the Free Software Foundation; either version 2 of the License, or
 *                                (at your option) any later version.
 *
 *                                This program is distributed in the hope that it will be useful,
 *                                but WITHOUT ANY WARRANTY; without even the implied warranty of
 *                                MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *                                GNU General Public License for more details.
 *
 *                                You should have received a copy of the GNU General Public License along
 *                                with this program; if not, write to the Free Software Foundation, Inc.,
 *                                51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 */

package org.labkey.sequenceanalysis.run.analysis;

import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.labkey.api.pipeline.PipelineJobException;
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
import org.labkey.api.sequenceanalysis.run.AbstractCommandWrapper;
import org.labkey.api.util.FileUtil;
import org.labkey.sequenceanalysis.run.util.HaplotypeCallerWrapper;
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

    public static class Provider extends AbstractAnalysisStepProvider<HaplotypeCallerAnalysis>
    {
        public Provider()
        {
            super("OncotatorAnalysis", "Oncotator Analysis", "GATK", "This will run GATK's Oncotator on the selected data. This tool annotates information onto genomic point mutations (SNPs/SNVs) and indels.", Arrays.asList(
                    ToolParameterDescriptor.create("multithreaded", "Multithreaded?", "If checked, this tool will attempt to run in multi-threaded mode.  There are sometimes issues with this.", "checkbox", null, null),
                    ToolParameterDescriptor.create("useQueue", "Use Queue?", "If checked, this tool will attempt to run using GATK queue.  This is the preferred way to multi-thread this tool.", "checkbox", new JSONObject()
                    {{
                            put("checked", true);
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
    public Output performAnalysisPerSampleRemote(Readset rs, File inputBam, ReferenceGenome referenceGenome, File outputDir) throws PipelineJobException
    {
        AnalysisOutputImpl output = new AnalysisOutputImpl();
        output.addInput(inputBam, "Input BAM File");

        File outputFile = new File(outputDir, FileUtil.getBaseName(inputBam) + ".g.vcf.gz");
        File idxFile = new File(outputDir, FileUtil.getBaseName(inputBam) + ".g.vcf.gz.idx");

        if (getProvider().getParameterByName("multithreaded").extractValue(getPipelineCtx().getJob(), getProvider(), Boolean.class, false))
        {
            getPipelineCtx().getLogger().debug("Oncotator will run multi-threaded");
            getWrapper().setMultiThreaded(true);
        }

        getWrapper().setOutputDir(outputDir);

        if (getProvider().getParameterByName("useQueue").extractValue(getPipelineCtx().getJob(), getProvider(), Boolean.class, false))
        {
            getWrapper().executeWithQueue(inputBam, referenceGenome.getWorkingFastaFile(), outputFile, getClientCommandArgs());
        }
        else
        {
            List<String> args = new ArrayList<>();
            args.addAll(getClientCommandArgs());
            args.add("--emitRefConfidence");
            args.add("GVCF");

            args.add("--variant_index_type");
            args.add("LINEAR");

            args.add("--variant_index_parameter");
            args.add("128000");

            getWrapper().execute(inputBam, referenceGenome.getWorkingFastaFile(), outputFile, args);
        }

        output.addOutput(outputFile, "gVCF File");
        output.addSequenceOutput(outputFile, rs.getName() + ": HaplotypeCaller Variants", "gVCF File", rs.getReadsetId(), null, referenceGenome.getGenomeId());
        if (idxFile.exists())
        {
            output.addOutput(idxFile, "VCF Index");
        }

        return output;
    }

    @Override
    public Output performAnalysisPerSampleLocal(AnalysisModel model, File inputBam, File referenceFasta) throws PipelineJobException
    {
        return null;
    }
}
