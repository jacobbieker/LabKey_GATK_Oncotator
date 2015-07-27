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

package org.labkey.sequenceanalysis.run.util;

import htsjdk.samtools.BAMIndexer;
import htsjdk.samtools.SAMFileReader;
import htsjdk.samtools.ValidationStringency;
import org.apache.log4j.Logger;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.resource.FileResource;
import org.labkey.api.util.Path;
import org.labkey.sequenceanalysis.SequenceAnalysisModule;
import org.labkey.sequenceanalysis.pipeline.SequenceTaskHelper;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

package org.labkey.sequenceanalysis.run.util;

import htsjdk.samtools.BAMIndexer;
import htsjdk.samtools.SAMFileReader;
import htsjdk.samtools.ValidationStringency;
import org.apache.log4j.Logger;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.resource.FileResource;
import org.labkey.api.util.Path;
import org.labkey.sequenceanalysis.SequenceAnalysisModule;
import org.labkey.sequenceanalysis.pipeline.SequenceTaskHelper;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by Jacob Bieker on 7/27/2015.
 */
public class OncotatorWrapper extends AbstractGatkWrapper
{
    private boolean _multiThreaded = false;

    public OncotatorWrapper(Logger log)
    {
        super(log);
    }

    public void setMultiThreaded(boolean multiThreaded)
    {
        _multiThreaded = multiThreaded;
    }

    public void execute(File inputBam, File referenceFasta, File outputFile, List<String> options) throws PipelineJobException
    {
        getLogger().info("Running GATK Oncotator for: " + inputBam.getName());

        ensureDictionary(referenceFasta);

        File expectedIndex = new File(inputBam.getPath() + ".bai");
        boolean doDeleteIndex = false;
        if (!expectedIndex.exists())
        {
            getLogger().debug("\tcreating temp index for BAM: " + inputBam.getName());
            //TODO: SamReaderFactory fact = SamReaderFactory.make();
            try (SAMFileReader reader = new SAMFileReader(inputBam))
            {
                reader.setValidationStringency(ValidationStringency.SILENT);
                BAMIndexer.createIndex(reader, expectedIndex);
            }

            doDeleteIndex = true;
        }
        else
        {
            getLogger().debug("\tusing existing index: " + expectedIndex.getPath());
        }

        List<String> args = new ArrayList<>();
        args.add("java");
        args.addAll(getBaseParams());
        args.add("-jar");
        args.add(getJAR().getPath());
        args.add("-T");
        args.add("Oncotator");
        args.add("-R");
        args.add(referenceFasta.getPath());
        args.add("-I");
        args.add(inputBam.getPath());
        args.add("-o");
        args.add(outputFile.getPath());
        if (options != null)
        {
            args.addAll(options);
        }

        if (_multiThreaded)
        {
            Integer maxThreads = SequenceTaskHelper.getMaxThreads(getLogger());
            if (maxThreads != null)
            {
                args.add("-nct");
                args.add(maxThreads.toString());
            }
        }

        execute(args);
        if (!outputFile.exists())
        {
            throw new PipelineJobException("Expected output not found: " + outputFile.getPath());
        }

        if (doDeleteIndex)
        {
            getLogger().debug("\tdeleting temp BAM index: " + expectedIndex.getPath());
            expectedIndex.delete();
        }
    }

    public void executeWithQueue(File inputBam, File referenceFasta, File outputFile, List<String> options) throws PipelineJobException
    {
        getLogger().info("Running GATK HaplotypeCaller using Queue for: " + inputBam.getName());

        ensureDictionary(referenceFasta);

        File expectedIndex = new File(inputBam.getPath() + ".bai");
        boolean doDeleteIndex = false;
        if (!expectedIndex.exists())
        {
            getLogger().debug("\tcreating temp index for BAM: " + inputBam.getName());
            //TODO: SamReaderFactory fact = SamReaderFactory.make();
            try (SAMFileReader reader = new SAMFileReader(inputBam))
            {
                reader.setValidationStringency(ValidationStringency.SILENT);
                BAMIndexer.createIndex(reader, expectedIndex);
            }

            doDeleteIndex = true;
        }
        else
        {
            getLogger().debug("\tusing existing index: " + expectedIndex.getPath());
        }

        try
        {
            Module module = ModuleLoader.getInstance().getModule(SequenceAnalysisModule.class);
            FileResource r = (FileResource)module.getModuleResolver().lookup(Path.parse("external/qscript/HaplotypeCallerRunner.scala"));
            File scalaScript = r.getFile();

            if (scalaScript == null)
                throw new FileNotFoundException("Not found: " + scalaScript);

            if (!scalaScript.exists())
                throw new FileNotFoundException("Not found: " + scalaScript.getPath());

            List<String> args = new ArrayList<>();
            args.add("java");
            args.addAll(getBaseParams());
            args.add("-classpath");
            args.add(getJAR().getPath());

            args.add("-jar");
            args.add(getQueueJAR().getPath());
            args.add("-S");
            args.add(scalaScript.getPath());
            args.add("-jobRunner");
            args.add("ParallelShell");
            args.add("-run");

            args.add("-R");
            args.add(referenceFasta.getPath());
            args.add("-I");
            args.add(inputBam.getPath());
            args.add("-o");
            args.add(outputFile.getPath());
            if (options != null)
            {
                args.addAll(options);
            }

            args.add("-startFromScratch");
            args.add("-scatterCount");
            Integer maxThreads = SequenceTaskHelper.getMaxThreads(getLogger());
            if (maxThreads != null)
            {
                args.add(maxThreads.toString());
            }
            else
            {
                args.add("1");
            }

            execute(args);
            if (!outputFile.exists())
            {
                throw new PipelineJobException("Expected output not found: " + outputFile.getPath());
            }

            if (doDeleteIndex)
            {
                getLogger().debug("\tdeleting temp BAM index: " + expectedIndex.getPath());
                expectedIndex.delete();
            }
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }
    }
}

