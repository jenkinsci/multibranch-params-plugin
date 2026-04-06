package io.jenkins.plugins.multibranchparams;

import hudson.model.BooleanParameterDefinition;
import hudson.model.ChoiceParameterDefinition;
import hudson.model.ParameterDefinition;
import hudson.model.StringParameterDefinition;
import jenkins.branch.BranchProperty;
import jenkins.branch.BranchSource;
import jenkins.branch.DefaultBranchPropertyStrategy;
import jenkins.plugins.git.GitSCMSource;
import org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.util.Arrays;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Integration tests for {@link ParameterizedBranchProperty}.
 *
 * <p>Uses {@link JenkinsRule} which spins up a real, temporary Jenkins instance.
 * These tests verify descriptor registration, config round-trips, and that
 * the property is correctly persisted and reloaded.
 */
public class ParameterizedBranchPropertyTest {

    @Rule
    public JenkinsRule jenkins = new JenkinsRule();

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private WorkflowMultiBranchProject createProject(ParameterizedBranchProperty property)
            throws Exception {
        WorkflowMultiBranchProject project =
                jenkins.createProject(WorkflowMultiBranchProject.class, "test-multibranch");

        GitSCMSource source = new GitSCMSource("https://github.com/example/repo.git");
        BranchSource branchSource = new BranchSource(source);
        branchSource.setStrategy(
                new DefaultBranchPropertyStrategy(new BranchProperty[]{property}));
        project.getSourcesList().add(branchSource);
        return project;
    }

    /**
     * Round-trips the project through Jenkins config serialisation and returns
     * the first branch property from the reloaded strategy.
     *
     * <p>Note: {@code DefaultBranchPropertyStrategy.getProps()} returns
     * {@code List<BranchProperty>}, not an array — use {@code .get(index)}.
     */
    private ParameterizedBranchProperty roundTrip(ParameterizedBranchProperty property)
            throws Exception {
        WorkflowMultiBranchProject project = createProject(property);
        jenkins.configRoundtrip(project);

        DefaultBranchPropertyStrategy strategy =
                (DefaultBranchPropertyStrategy) project.getSourcesList().get(0).getStrategy();
        // getProps() returns List<BranchProperty> — NOT an array
        return (ParameterizedBranchProperty) strategy.getProps().get(0);
    }

    // -------------------------------------------------------------------------
    // Descriptor tests
    // -------------------------------------------------------------------------

    @Test
    public void descriptor_isRegistered() {
        ParameterizedBranchProperty.DescriptorImpl desc =
                jenkins.jenkins.getDescriptorByType(ParameterizedBranchProperty.DescriptorImpl.class);
        assertNotNull("DescriptorImpl must be registered as a Jenkins extension", desc);
    }

    @Test
    public void descriptor_displayName() {
        ParameterizedBranchProperty.DescriptorImpl desc =
                jenkins.jenkins.getDescriptorByType(ParameterizedBranchProperty.DescriptorImpl.class);
        assertEquals("Branch Parameters", desc.getDisplayName());
    }

    @Test
    public void descriptor_exposesParameterTypes() {
        ParameterizedBranchProperty.DescriptorImpl desc =
                jenkins.jenkins.getDescriptorByType(ParameterizedBranchProperty.DescriptorImpl.class);

        List<ParameterDefinition.ParameterDescriptor> paramDescs = desc.getParameterDescriptors();

        assertThat(paramDescs, is(not(empty())));
        assertTrue("StringParameterDefinition must be listed",
                paramDescs.stream().anyMatch(d -> d.clazz == StringParameterDefinition.class));
        assertTrue("BooleanParameterDefinition must be listed",
                paramDescs.stream().anyMatch(d -> d.clazz == BooleanParameterDefinition.class));
        assertTrue("ChoiceParameterDefinition must be listed",
                paramDescs.stream().anyMatch(d -> d.clazz == ChoiceParameterDefinition.class));
    }

    // -------------------------------------------------------------------------
    // Config round-trip tests
    // -------------------------------------------------------------------------

    @Test
    public void roundTrip_noParams() throws Exception {
        ParameterizedBranchProperty prop = new ParameterizedBranchProperty();
        ParameterizedBranchProperty rt = roundTrip(prop);

        assertThat("Empty param list survives round-trip", rt.getParameterDefinitions(), is(empty()));
        assertEquals("parameterPolicy defaults to REPLACE",
                ParameterPolicy.REPLACE, rt.getParameterPolicy());
    }

    @Test
    public void roundTrip_stringParam() throws Exception {
        ParameterizedBranchProperty prop = new ParameterizedBranchProperty();
        prop.setParameterDefinitions(List.of(
                new StringParameterDefinition("DEPLOY_ENV", "staging", "Target environment")));
        prop.setParameterPolicy(ParameterPolicy.MERGE_PLUGIN_WINS);

        ParameterizedBranchProperty rt = roundTrip(prop);

        assertThat(rt.getParameterDefinitions(), hasSize(1));
        StringParameterDefinition param =
                (StringParameterDefinition) rt.getParameterDefinitions().get(0);
        assertEquals("DEPLOY_ENV", param.getName());
        assertEquals("staging", param.getDefaultValue());
        assertEquals("Target environment", param.getDescription());
        assertEquals(ParameterPolicy.MERGE_PLUGIN_WINS, rt.getParameterPolicy());
    }

    @Test
    public void roundTrip_booleanParam() throws Exception {
        ParameterizedBranchProperty prop = new ParameterizedBranchProperty();
        prop.setParameterDefinitions(List.of(
                new BooleanParameterDefinition("DRY_RUN", true, "Skip deployment")));

        ParameterizedBranchProperty rt = roundTrip(prop);

        assertThat(rt.getParameterDefinitions(), hasSize(1));
        BooleanParameterDefinition param =
                (BooleanParameterDefinition) rt.getParameterDefinitions().get(0);
        assertEquals("DRY_RUN", param.getName());
        assertTrue(param.isDefaultValue());
    }

    @Test
    public void roundTrip_choiceParam() throws Exception {
        ParameterizedBranchProperty prop = new ParameterizedBranchProperty();
        prop.setParameterDefinitions(List.of(
                new ChoiceParameterDefinition("REGION",
                        new String[]{"us-east-1", "eu-west-1", "ap-southeast-1"},
                        "AWS region")));

        ParameterizedBranchProperty rt = roundTrip(prop);

        assertThat(rt.getParameterDefinitions(), hasSize(1));
        ChoiceParameterDefinition param =
                (ChoiceParameterDefinition) rt.getParameterDefinitions().get(0);
        assertEquals("REGION", param.getName());
        assertThat(param.getChoices(), hasSize(3));
    }

    @Test
    public void roundTrip_multipleParamTypes() throws Exception {
        ParameterizedBranchProperty prop = new ParameterizedBranchProperty();
        prop.setParameterDefinitions(Arrays.asList(
                new StringParameterDefinition("VERSION", "1.0.0", ""),
                new BooleanParameterDefinition("DRY_RUN", false, ""),
                new ChoiceParameterDefinition("ENV", new String[]{"dev", "staging", "prod"}, "")
        ));

        ParameterizedBranchProperty rt = roundTrip(prop);

        List<ParameterDefinition> params = rt.getParameterDefinitions();
        assertThat(params, hasSize(3));
        assertThat(params.get(0), instanceOf(StringParameterDefinition.class));
        assertThat(params.get(1), instanceOf(BooleanParameterDefinition.class));
        assertThat(params.get(2), instanceOf(ChoiceParameterDefinition.class));
    }

    // -------------------------------------------------------------------------
    // jobDecorator tests
    // -------------------------------------------------------------------------

    @Test
    public void jobDecorator_isNullWhenNoParams() {
        ParameterizedBranchProperty prop = new ParameterizedBranchProperty();
        assertThat(prop.jobDecorator(org.jenkinsci.plugins.workflow.job.WorkflowJob.class),
                is(nullValue()));
    }

    @Test
    public void jobDecorator_isNonNullWhenParamsDefined() {
        ParameterizedBranchProperty prop = new ParameterizedBranchProperty();
        prop.setParameterDefinitions(List.of(
                new StringParameterDefinition("X", "y", "")));
        assertThat(prop.jobDecorator(org.jenkinsci.plugins.workflow.job.WorkflowJob.class),
                is(notNullValue()));
    }

    // -------------------------------------------------------------------------
    // Project-level registration test
    // -------------------------------------------------------------------------

    @Test
    public void property_attachesToProject() throws Exception {
        ParameterizedBranchProperty prop = new ParameterizedBranchProperty();
        prop.setParameterDefinitions(List.of(
                new StringParameterDefinition("MY_PARAM", "hello", "")));

        WorkflowMultiBranchProject project = createProject(prop);

        DefaultBranchPropertyStrategy strategy =
                (DefaultBranchPropertyStrategy) project.getSourcesList().get(0).getStrategy();

        // getProps() returns List<BranchProperty> — use hasSize() and .get(), not arrayWithSize/[]
        assertThat(strategy.getProps(), hasSize(1));
        assertThat(strategy.getProps().get(0), instanceOf(ParameterizedBranchProperty.class));
    }
}
