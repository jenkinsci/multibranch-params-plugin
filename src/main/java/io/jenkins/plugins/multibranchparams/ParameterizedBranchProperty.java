package io.jenkins.plugins.multibranchparams;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.Job;
import hudson.model.JobProperty;
import hudson.model.ParameterDefinition;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.Run;
import hudson.util.ListBoxModel;
import jenkins.branch.BranchProperty;
import jenkins.branch.BranchPropertyDescriptor;
import jenkins.branch.JobDecorator;
import jenkins.branch.MultiBranchProject;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * A {@link BranchProperty} that injects build parameters into every branch job
 * of a Multibranch Pipeline from the UI, without touching individual Jenkinsfiles.
 *
 * <p>Attach this property via:
 * <ul>
 *   <li>Multibranch Pipeline → Configure → Branch Sources → Property strategy
 *       → All branches get the same properties → Add property → Branch Parameters</li>
 *   <li>Or use {@link ParameterizedBranchPropertyStrategy} for regex-based filtering.</li>
 * </ul>
 *
 * <h3>Parameter policy</h3>
 * <p>The {@link #parameterPolicy} field controls how plugin-defined parameters
 * interact with any {@code parameters {}} block in the Jenkinsfile.
 * See {@link ParameterPolicy} for a full description of each mode.
 */
public class ParameterizedBranchProperty extends BranchProperty {

    private static final Logger LOGGER =
            Logger.getLogger(ParameterizedBranchProperty.class.getName());

    /** Parameter definitions configured through the UI. */
    private List<ParameterDefinition> parameterDefinitions = new ArrayList<>();

    /** Controls how scan-time injection interacts with Jenkinsfile-set parameters. */
    private ParameterPolicy parameterPolicy = ParameterPolicy.REPLACE;

    // -------------------------------------------------------------------------
    // Construction
    // -------------------------------------------------------------------------

    /** Required by Stapler for data binding. */
    @DataBoundConstructor
    public ParameterizedBranchProperty() {
    }

    // -------------------------------------------------------------------------
    // Getters / Setters (Stapler data-bound)
    // -------------------------------------------------------------------------

    public List<ParameterDefinition> getParameterDefinitions() {
        return Collections.unmodifiableList(parameterDefinitions);
    }

    @DataBoundSetter
    public void setParameterDefinitions(@NonNull List<ParameterDefinition> parameterDefinitions) {
        this.parameterDefinitions = new ArrayList<>(parameterDefinitions);
    }

    public ParameterPolicy getParameterPolicy() {
        return parameterPolicy;
    }

    @DataBoundSetter
    public void setParameterPolicy(@NonNull ParameterPolicy parameterPolicy) {
        this.parameterPolicy = parameterPolicy;
    }

    // -------------------------------------------------------------------------
    // Core: branch job decoration
    // -------------------------------------------------------------------------

    @Override
    public <P extends Job<P, B>, B extends Run<P, B>>
    JobDecorator<P, B> jobDecorator(Class<P> clazz) {

        if (parameterDefinitions == null || parameterDefinitions.isEmpty()) {
            return null;
        }

        return new JobDecorator<P, B>() {
            @NonNull
            @Override
            public List<JobProperty<? super P>> jobProperties(
                    @NonNull List<JobProperty<? super P>> jobProperties) {

                List<ParameterDefinition> effective = resolveEffectiveParams(jobProperties);

                // null signals SKIP_IF_JENKINSFILE decided to leave existing params untouched
                if (effective == null) {
                    LOGGER.fine(() -> "Skipping parameter injection — Jenkinsfile owns params.");
                    return jobProperties;
                }

                List<JobProperty<? super P>> result = new ArrayList<>();
                boolean injected = false;

                for (JobProperty<? super P> prop : jobProperties) {
                    if (prop instanceof ParametersDefinitionProperty) {
                        result.add(new ParametersDefinitionProperty(effective));
                        injected = true;
                    } else {
                        result.add(prop);
                    }
                }

                if (!injected) {
                    result.add(new ParametersDefinitionProperty(effective));
                }

                LOGGER.fine(() -> "Injected " + effective.size()
                        + " parameter(s) into branch job properties.");
                return result;
            }

            /**
             * Resolves the effective parameter list based on {@link #parameterPolicy}.
             *
             * <ul>
             *   <li>REPLACE:            {@code [Header, UI params]}</li>
             *   <li>MERGE:              {@code [Jenkinsfile-only params, Header, UI params]}</li>
             *   <li>SKIP_IF_JENKINSFILE: {@code null} when Jenkinsfile owns params,
             *                            {@code [Header, UI params]} otherwise</li>
             * </ul>
             *
             * @return the list to inject, or {@code null} to leave existing params untouched
             */
            private List<ParameterDefinition> resolveEffectiveParams(
                    List<JobProperty<? super P>> existingProps) {

                switch (parameterPolicy) {

                    case REPLACE:
                        return buildUiParamList();

                    case MERGE: {
                        Set<String> uiNames = parameterDefinitions.stream()
                                .map(ParameterDefinition::getName)
                                .collect(Collectors.toSet());

                        List<ParameterDefinition> jenkinsfileOnly = new ArrayList<>();
                        for (JobProperty<? super P> prop : existingProps) {
                            if (prop instanceof ParametersDefinitionProperty) {
                                ((ParametersDefinitionProperty) prop).getParameterDefinitions()
                                        .stream()
                                        .filter(p -> !(p instanceof MultiBranchHeaderParameter))
                                        .filter(p -> !uiNames.contains(p.getName()))
                                        .forEach(jenkinsfileOnly::add);
                            }
                        }

                        // [Jenkinsfile-only params …, Header, UI params …]
                        List<ParameterDefinition> merged = new ArrayList<>(jenkinsfileOnly);
                        merged.add(new MultiBranchHeaderParameter());
                        merged.addAll(parameterDefinitions);
                        return merged;
                    }

                    case SKIP_IF_JENKINSFILE: {
                        for (JobProperty<? super P> prop : existingProps) {
                            if (prop instanceof ParametersDefinitionProperty) {
                                List<ParameterDefinition> current =
                                        ((ParametersDefinitionProperty) prop).getParameterDefinitions();
                                boolean hasRealParams = current.stream()
                                        .anyMatch(p -> !(p instanceof MultiBranchHeaderParameter));
                                boolean hasHeader = current.stream()
                                        .anyMatch(p -> p instanceof MultiBranchHeaderParameter);
                                if (hasRealParams && !hasHeader) {
                                    // Params exist and were NOT set by this plugin → Jenkinsfile owns them
                                    return null;
                                }
                            }
                        }
                        // No Jenkinsfile-owned params found → inject
                        return buildUiParamList();
                    }

                    default:
                        return buildUiParamList();
                }
            }

            /** Builds {@code [Header, UI params]}. */
            private List<ParameterDefinition> buildUiParamList() {
                List<ParameterDefinition> list = new ArrayList<>();
                list.add(new MultiBranchHeaderParameter());
                list.addAll(parameterDefinitions);
                return list;
            }
        };
    }

    // -------------------------------------------------------------------------
    // Descriptor
    // -------------------------------------------------------------------------

    /** Jenkins extension descriptor for {@link ParameterizedBranchProperty}. */
    @Extension
    @Symbol("parameterizedBranch")
    public static class DescriptorImpl extends BranchPropertyDescriptor {

        @NonNull
        @Override
        public String getDisplayName() {
            return "Branch Parameters";
        }

        public List<ParameterDefinition.ParameterDescriptor> getParameterDescriptors() {
            return ParameterDefinition.all().stream()
                    .filter(d -> !(d instanceof MultiBranchHeaderParameter.DescriptorImpl))
                    .collect(Collectors.toList());
        }

        public ListBoxModel doFillParameterPolicyItems() {
            ListBoxModel model = new ListBoxModel();
            model.add("Always replace (plugin params override Jenkinsfile)", ParameterPolicy.REPLACE.name());
            model.add("Merge (keep Jenkinsfile params, plugin wins on conflict)", ParameterPolicy.MERGE.name());
            model.add("Skip if Jenkinsfile owns params (inject only until first Jenkinsfile build)", ParameterPolicy.SKIP_IF_JENKINSFILE.name());
            return model;
        }

        @Override
        @SuppressWarnings("rawtypes")
        public boolean isApplicable(MultiBranchProject project) {
            return true;
        }
    }
}
