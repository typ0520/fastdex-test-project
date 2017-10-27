/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.build.gradle.tasks;

import static com.google.common.base.Preconditions.checkNotNull;

import com.android.annotations.NonNull;
import com.android.build.gradle.external.gson.NativeBuildConfigValue;
import com.android.build.gradle.external.gson.NativeLibraryValue;
import com.android.build.gradle.internal.core.Abi;
import com.android.build.gradle.internal.ndk.NdkHandler;
import com.android.build.gradle.internal.scope.TaskConfigAction;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.tasks.BaseTask;
import com.android.build.gradle.internal.variant.BaseVariantData;
import com.android.builder.core.AndroidBuilder;
import com.android.ide.common.process.ProcessException;
import com.android.ide.common.process.ProcessInfoBuilder;
import com.android.utils.FileUtils;
import com.android.utils.StringHelper;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.gradle.api.tasks.TaskAction;

/**
 * Task that takes set of JSON files of type NativeBuildConfigValue and does clean steps with them.
 *
 * <p>It declares no inputs or outputs, as it's supposed to always run when invoked. Incrementality
 * is left to the underlying build system.
 */
public class ExternalNativeCleanTask extends BaseTask {

    private List<File> nativeBuildConfigurationsJsons;

    private File objFolder;

    private Map<Abi, File> stlSharedObjectFiles;

    /** Log low level diagnostic information. */
    protected void diagnostic(String format, Object... args) {
        getLogger().info(String.format(getName() + ": " + format, args));
    }

    @TaskAction
    void clean() throws ProcessException, IOException {
        diagnostic("starting clean");
        diagnostic("finding existing JSONs");

        List<File> existingJsons = Lists.newArrayList();
        for (File json : nativeBuildConfigurationsJsons) {
            if (json.isFile()) {
                existingJsons.add(json);
            }
        }

        Collection<NativeBuildConfigValue> configValueList = ExternalNativeBuildTaskUtils
                .getNativeBuildConfigValues(
                        existingJsons, checkNotNull(getVariantName()));
        List<String> cleanCommands = Lists.newArrayList();
        List<String> targetNames = Lists.newArrayList();
        for (NativeBuildConfigValue config : configValueList) {
            if (config.libraries == null) {
                continue;
            }
            if (config.cleanCommands != null) {
                cleanCommands.addAll(config.cleanCommands);
                Set<String> targets = Sets.newHashSet();
                for (NativeLibraryValue library : config.libraries.values()) {
                    targets.add(String.format("%s %s", library.artifactName, library.abi));
                }
                targetNames.add(Joiner.on(",").join(targets));
            }
        }
        diagnostic("about to execute %s clean commands", cleanCommands.size());
        executeProcessBatch(cleanCommands, targetNames);

        if (!stlSharedObjectFiles.isEmpty()) {
            diagnostic("remove STL shared object files");
            for (Abi abi : stlSharedObjectFiles.keySet()) {
                File stlSharedObjectFile = checkNotNull(stlSharedObjectFiles.get(abi));
                File objAbi = FileUtils.join(objFolder, abi.getName(),
                        stlSharedObjectFile.getName());

                if (objAbi.delete()) {
                    diagnostic("removed file %s", objAbi);
                } else {
                    diagnostic("failed to remove file %s", objAbi);
                }
            }
        }
        diagnostic("clean complete");
    }

    /**
     * Given a list of build commands, execute each. If there is a failure, processing is stopped at
     * that point.
     */
    protected void executeProcessBatch(@NonNull List<String> commands,
            @NonNull List<String> targetNames) throws ProcessException, IOException {
        for (int commandIndex = 0; commandIndex < commands.size(); ++commandIndex) {
            String command = commands.get(commandIndex);
            String target = targetNames.get(commandIndex);
            getLogger().lifecycle(String.format("Clean %s", target));
            List<String> tokens = StringHelper.tokenizeString(command);
            ProcessInfoBuilder processBuilder = new ProcessInfoBuilder();
            processBuilder.setExecutable(tokens.get(0));
            for (int i = 1; i < tokens.size(); ++i) {
                processBuilder.addArgs(tokens.get(i));
            }
            diagnostic("%s", processBuilder);
            ExternalNativeBuildTaskUtils.executeBuildProcessAndLogError(
                    getBuilder(),
                    processBuilder,
                    true /* logStdioToInfo */);
        }
    }

    public static class ConfigAction implements TaskConfigAction<ExternalNativeCleanTask> {
        @NonNull
        private final ExternalNativeJsonGenerator generator;
        @NonNull
        private final VariantScope scope;
        @NonNull
        private final AndroidBuilder androidBuilder;

        public ConfigAction(
                @NonNull ExternalNativeJsonGenerator generator,
                @NonNull VariantScope scope,
                @NonNull AndroidBuilder androidBuilder) {
            this.generator = generator;
            this.scope = scope;
            this.androidBuilder = androidBuilder;
        }

        @NonNull
        @Override
        public String getName() {
            return scope.getTaskName("externalNativeBuildClean");
        }

        @NonNull
        @Override
        public Class<ExternalNativeCleanTask> getType() {
            return ExternalNativeCleanTask.class;
        }

        @Override
        public void execute(@NonNull ExternalNativeCleanTask task) {
            final BaseVariantData variantData = scope.getVariantData();
            task.setVariantName(variantData.getName());

            // Attempt to clean every possible ABI even those that aren't currently built.
            // This covers cases where user has changed abiFilters or platform. We don't want
            // to leave stale results hanging around.
            List<String> abiNames = Lists.newArrayList();
            for(Abi abi : NdkHandler.getAbiList()) {
                abiNames.add(abi.getName());
            }
            task.setAndroidBuilder(androidBuilder);
            task.nativeBuildConfigurationsJsons =
                    ExternalNativeBuildTaskUtils.getOutputJsons(
                            generator.getJsonFolder(), abiNames);
            task.stlSharedObjectFiles = generator.getStlSharedObjectFiles();
            task.objFolder = generator.getObjFolder();
        }
    }
}
