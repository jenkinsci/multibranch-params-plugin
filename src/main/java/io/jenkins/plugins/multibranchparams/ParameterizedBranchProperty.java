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
import jenkins.model.Jenkins;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.verb.POST;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
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
 * <h2>Parameter policy</h2>
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
             *   <li>REPLACE:               {@code [Header, UI params]}</li>
             *   <li>MERGE_PLUGIN_WINS:     {@code [Jenkinsfile-only params, Header, UI params]}</li>
             *   <li>MERGE_JENKINSFILE_WINS:{@code [All Jenkinsfile params, Header, plugin-only params]}</li>
             *   <li>SKIP_IF_JENKINSFILE:   {@code null} when Jenkinsfile owns params,
             *                              {@code [Header, UI params]} otherwise</li>
             * </ul>
             *
             * @return the list to inject, or {@code null} to leave existing params untouched
             */
            private List<ParameterDefinition> resolveEffectiveParams(
                    List<JobProperty<? super P>> existingProps) {

                switch (parameterPolicy) {
                    case REPLACE:
                        return buildUiParamList();
                    case MERGE_PLUGIN_WINS:
                        return mergePluginWins(existingProps);
                    case MERGE_JENKINSFILE_WINS:
                        return mergeJenkinsfileWins(existingProps);
                    case SKIP_IF_JENKINSFILE:
                        return skipIfJenkinsfileOwns(existingProps);
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

            /**
             * MERGE_PLUGIN_WINS: Jenkinsfile-only params appear first, then the header,
             * then all plugin params. On a name conflict the plugin definition is used.
             * Result: {@code [Jenkinsfile-only…, Header, UI params…]}
             */
            private List<ParameterDefinition> mergePluginWins(
                    List<JobProperty<? super P>> existingProps) {
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
                List<ParameterDefinition> merged = new ArrayList<>(jenkinsfileOnly);
                merged.add(new MultiBranchHeaderParameter());
                merged.addAll(parameterDefinitions);
                return merged;
            }

            /**
             * MERGE_JENKINSFILE_WINS: all Jenkinsfile params are kept as-is first,
             * then the header, then only the plugin params whose names are NOT already
             * covered by the Jenkinsfile. On a name conflict the Jenkinsfile wins.
             * Result: {@code [All Jenkinsfile params…, Header, plugin-only params…]}
             *
             * <p>If the existing params still contain the plugin's header marker they were
             * injected by a previous scan (no Jenkinsfile build has run yet), so we fall
             * back to injecting the full plugin param list — same as the first scan.
             */
            private List<ParameterDefinition> mergeJenkinsfileWins(
                    List<JobProperty<? super P>> existingProps) {
                boolean existingArePluginInjected = existingProps.stream()
                        .filter(p -> p instanceof ParametersDefinitionProperty)
                        .flatMap(p -> ((ParametersDefinitionProperty) p)
                                .getParameterDefinitions().stream())
                        .anyMatch(p -> p instanceof MultiBranchHeaderParameter);

                if (existingArePluginInjected) {
                    // No Jenkinsfile build has run yet — inject plugin params as usual
                    return buildUiParamList();
                }

                Set<String> jenkinsfileNames = new HashSet<>();
                List<ParameterDefinition> jenkinsfileParams = new ArrayList<>();
                for (JobProperty<? super P> prop : existingProps) {
                    if (prop instanceof ParametersDefinitionProperty) {
                        ((ParametersDefinitionProperty) prop).getParameterDefinitions()
                                .stream()
                                .filter(p -> !(p instanceof MultiBranchHeaderParameter))
                                .forEach(p -> {
                                    jenkinsfileParams.add(p);
                                    jenkinsfileNames.add(p.getName());
                                });
                    }
                }
                List<ParameterDefinition> pluginOnly = parameterDefinitions.stream()
                        .filter(p -> !jenkinsfileNames.contains(p.getName()))
                        .collect(Collectors.toList());
                List<ParameterDefinition> merged = new ArrayList<>(jenkinsfileParams);
                merged.add(new MultiBranchHeaderParameter());
                merged.addAll(pluginOnly);
                return merged;
            }

            /**
             * SKIP_IF_JENKINSFILE policy: returns {@code null} (leave untouched) when the
             * job already has params that were NOT set by this plugin (no header marker),
             * otherwise injects normally.
             */
            private List<ParameterDefinition> skipIfJenkinsfileOwns(
                    List<JobProperty<? super P>> existingProps) {
                for (JobProperty<? super P> prop : existingProps) {
                    if (prop instanceof ParametersDefinitionProperty) {
                        List<ParameterDefinition> current =
                                ((ParametersDefinitionProperty) prop).getParameterDefinitions();
                        boolean hasRealParams = current.stream()
                                .anyMatch(p -> !(p instanceof MultiBranchHeaderParameter));
                        boolean hasHeader = current.stream()
                                .anyMatch(p -> p instanceof MultiBranchHeaderParameter);
                        if (hasRealParams && !hasHeader) {
                            return null;
                        }
                    }
                }
                return buildUiParamList();
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

        @POST
        public ListBoxModel doFillParameterPolicyItems() {
            Jenkins.get().checkPermission(Jenkins.READ);
            ListBoxModel model = new ListBoxModel();
            model.add("Always replace (use only Multibranch params, ignore Jenkinsfile)", ParameterPolicy.REPLACE.name());
            model.add("Merge — Multibranch wins on conflict", ParameterPolicy.MERGE_PLUGIN_WINS.name());
            model.add("Merge — Jenkinsfile wins on conflict", ParameterPolicy.MERGE_JENKINSFILE_WINS.name());
            model.add("Dismiss Multibranch if Jenkinsfile defines any params", ParameterPolicy.SKIP_IF_JENKINSFILE.name());
            return model;
        }

    }
}
