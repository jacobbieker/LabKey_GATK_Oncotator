package org.labkey.sequenceanalysis.run.util;

import htsjdk.samtools.BAMIndexer;
import htsjdk.samtools.SAMFileReader;
import htsjdk.samtools.ValidationStringency;
import org.apache.log4j.Logger;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.resource.FileResource;
import org.labkey.api.sequenceanalysis.run.AbstractCommandWrapper;
import org.labkey.api.util.Path;
import org.labkey.sequenceanalysis.SequenceAnalysisModule;
import org.labkey.sequenceanalysis.pipeline.SequenceTaskHelper;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

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
 * Created by Jacob Bieker on 8/8/2014.
 */
public class OncotatorWrapper extends AbstractCommandWrapper
{

    public OncotatorWrapper(Logger log)
    {
        super(log);
    }

    public void execute(File inputVcf, File outputFile, List<String> options) throws PipelineJobException
    {
        getLogger().info("Running Oncotator for: " + inputVcf.getName());

        List<String> args = new ArrayList<>();
        args.add("source <venv>/bin/activate");
        args.add(inputVcf.getPath()); // Set to get virtualenv location
        args.add("oncotator"); //To start Oncotator and run the rest of the commands
        if (options != null)
        {
            args.addAll(options);
        }
        args.add(inputVcf.getPath());
        args.add(outputFile.getPath());
        args.add("hg19");

        args.add("deactivate"); //Shut down virtualenv
        execute(args);
        if (!outputFile.exists())
        {
            throw new PipelineJobException("Expected output not found: " + outputFile.getPath());
        }
    }
}

