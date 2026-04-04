# Multibranch Pipeline Parameterization Plugin

A Jenkins plugin that adds UI-defined build parameters to Multibranch Pipeline jobs.

## The Problem

Jenkins Multibranch Pipelines dynamically create branch jobs on each index scan and
**overwrite their configurations**, wiping out any manually added parameters. The only
supported way to define parameters is inside the `Jenkinsfile`:

```groovy
pipeline {
    parameters {
        string(name: 'DEPLOY_ENV', defaultValue: 'staging')
    }
    ...
}
```

This has drawbacks:

- Parameters are developer-controlled and scattered across branches
- Ops teams can't centrally manage deployment parameters
- Any change requires a commit to every branch

## The Solution

This plugin lets you define parameters **once, at the Multibranch folder level**, via the
Jenkins UI. They are injected into every branch job on each scan and survive rescans.

---

## Installation

### From source

```bash
git clone https://github.com/your-org/multibranch-params-plugin.git
cd multibranch-params-plugin
mvn clean package -DskipTests
# Install the generated target/multibranch-params.hpi via Jenkins → Manage → Plugins → Advanced
```

### Requirements

| Component | Minimum version |
|---|---|
| Jenkins | 2.426.3 |
| Java | 11 |
| Branch API Plugin | 2.1118.x |
| Workflow Multibranch Plugin | 756.x |

---

## Usage

### Option A — Apply parameters to ALL branches

1. Open your Multibranch Pipeline job → **Configure**
2. Under **Branch Sources**, find **Property strategy**
3. Select **All branches get the same properties**
4. Click **Add property** → **Branch Parameters**
5. Click **Add Parameter** and configure your parameters
6. **Save** and trigger a branch index scan

Every branch will now show **Build with Parameters** with your defined params.

### Option B — Apply parameters to SPECIFIC branches (regex filter)

1. Under **Property strategy**, select **Parameterized Branch Strategy**
2. Choose a **Filter mode**:
   - `All branches` — same as Option A
   - `Only branches matching pattern` — regex include list
   - `All branches except those matching pattern` — regex exclude list
3. Enter a **Branch name pattern** (Java regex), e.g.:
   - `main|develop` — only those two branches
   - `release/.*` — all release branches
   - `feature/.*` — all feature branches
4. Add parameters and save

### Pipeline / JCasC (Configuration as Code)

```yaml
jobs:
  - script: |
      multibranchPipelineJob('my-service') {
        branchSources {
          git {
            id('my-repo')
            remote('https://github.com/example/my-service.git')
          }
        }
        factory {
          workflowBranchProjectFactory {
            scriptPath('Jenkinsfile')
          }
        }
        configure {
          it / sources / data / 'jenkins.branch.BranchSource' / strategy(
            class: 'jenkins.branch.DefaultBranchPropertyStrategy') {
              props {
                'io.jenkins.plugins.multibranchparams.ParameterizedBranchProperty' {
                  parameterDefinitions {
                    'hudson.model.StringParameterDefinition' {
                      name('DEPLOY_ENV')
                      defaultValue('staging')
                      description('Target deployment environment')
                    }
                    'hudson.model.BooleanParameterDefinition' {
                      name('DRY_RUN')
                      defaultValue('false')
                      description('Skip actual deployment steps')
                    }
                  }
                  mergeWithJenkinsfileParams(false)
                }
              }
            }
        }
      }
```

---

## Parameter Merge Behaviour

| Setting | Behaviour |
|---|---|
| **Merge unchecked** (default) | UI-defined parameters completely replace any `parameters {}` block in the Jenkinsfile |
| **Merge checked** | UI params are combined with Jenkinsfile params. On name conflict, the UI param wins |

---

## Supported Parameter Types

All built-in Jenkins parameter types work out of the box:

- String Parameter
- Boolean Parameter
- Choice Parameter
- Password Parameter
- File Parameter
- Run Parameter
- Multi-line String Parameter

Third-party parameter plugins (e.g., Extended Choice Parameter, Git Parameter) are also
automatically available in the dropdown.

---

## Building & Testing

```bash
# Run all tests
mvn test

# Run only unit tests (no Jenkins instance)
mvn test -Dtest=ParameterizedBranchPropertyStrategyTest

# Run integration tests (spins up Jenkins)
mvn test -Dtest=ParameterizedBranchPropertyTest

# Start a local Jenkins with the plugin pre-installed (great for manual testing)
mvn hpi:run
# Then open http://localhost:8080/jenkins
```

---

## Architecture

```
ParameterizedBranchProperty          (extends BranchProperty)
│  ├── jobDecorator()                 ← called by Branch API on every scan
│  │    └── jobProperties()           ← injects ParametersDefinitionProperty
│  └── DescriptorImpl                 ← registers with Jenkins, provides UI
│
ParameterizedBranchPropertyStrategy  (extends BranchPropertyStrategy)
│  ├── getPropertiesFor(SCMHead)      ← decides which branches get params
│  └── DescriptorImpl
│
config.jelly (×2)                     ← Jelly UI rendered in Configure page
config.properties (×2)               ← i18n strings
```

---

## Contributing

Pull requests are welcome. Please add tests for any new behaviour and run
`mvn checkstyle:check` before submitting.

---

## License

MIT
