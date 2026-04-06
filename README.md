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

Parameters injected by the plugin are clearly marked on the **Build with Parameters** page
with a banner: _"The parameters below are managed by the Multibranch Pipeline configuration,
not the Jenkinsfile."_

---

## Installation

### From GitHub Releases

Download the `.hpi` file from the [Releases](../../releases) page and install it via
**Jenkins → Manage Jenkins → Plugins → Advanced → Upload Plugin**.

### From source

```bash
git clone https://github.com/your-org/multibranch-params-plugin.git
cd multibranch-params-plugin
mvn clean package -DskipTests
# Install target/multibranch-params.hpi via Jenkins → Manage Jenkins → Plugins → Advanced
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
6. Choose a **Jenkinsfile parameter policy** (see below)
7. **Save** and trigger a branch index scan

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
4. Add parameters, choose a policy, and save

---

## Jenkinsfile Parameter Policy

Controls what happens when a branch's Jenkinsfile also defines parameters via
`properties([parameters([...])])`.

| Policy | Behaviour |
|---|---|
| **Always replace** _(default)_ | Only Multibranch params are used. Any params declared in the Jenkinsfile are ignored. The Jenkinsfile can still overwrite them at build time, but the next scan restores the Multibranch params. |
| **Merge — Multibranch wins** | Params from both sources are combined. Jenkinsfile-only params appear above the banner. On a name conflict the Multibranch definition takes precedence. |
| **Merge — Jenkinsfile wins** | Params from both sources are combined. All Jenkinsfile params are kept as-is above the banner. On a name conflict the Jenkinsfile definition is kept. Only Multibranch params whose names are not already in the Jenkinsfile are added below the banner. |
| **Dismiss if Jenkinsfile defines any** | Multibranch params are injected only until the first Jenkinsfile build runs. Once a build has stored its own `parameters {}` block, subsequent scans leave those params untouched. Useful when Jenkinsfiles are expected to be the long-term owner of their parameters. |

> **Note:** No policy can prevent a running Jenkinsfile from calling `properties()` and
> overwriting parameters at build time — that happens inside the pipeline execution.
> The plugin operates exclusively at scan time.

---

## Visual indicator

On the **Build with Parameters** page, plugin-injected parameters are preceded by a
labelled banner so it is always clear which parameters are centrally managed:

```
┌─────────────────────────────────────────────────────────┐
│  📋  MULTIBRANCH PIPELINE CONFIGURATION                  │
│  The parameters below are managed by the Multibranch    │
│  Pipeline configuration, not the Jenkinsfile.           │
└─────────────────────────────────────────────────────────┘
  DEPLOY_ENV   [ staging        ]
  DRY_RUN      [ ☐ ]
```

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
automatically available in the **Add Parameter** dropdown.

---

## Job DSL

The plugin integrates natively with the [Job DSL plugin](https://plugins.jenkins.io/job-dsl/)
via Dynamic DSL — no extra configuration required. Use the `branchSource {}` block
(singular) inside `branchSources {}` to co-locate the SCM source and the branch strategy
in one entry.

Use `parameterizedBranchStrategy` in all cases — set `filterMode('ALL')` to apply
parameters to every branch, or `filterMode('INCLUDE_PATTERN')` / `filterMode('EXCLUDE_PATTERN')`
to target specific branches by regex.

### Apply parameters to all branches

```groovy
multibranchPipelineJob('my-service') {
  branchSources {
    branchSource {
      source {
        git {
          id('my-repo')
          remote('https://github.com/example/my-service.git')
        }
      }
      strategy {
        parameterizedBranchStrategy {
          filterMode('ALL')
          parameterPolicy('REPLACE')
          parameterDefinitions {
            stringParam {
              name('DEPLOY_ENV')
              defaultValue('staging')
              description('Target environment')
            }
            booleanParam {
              name('DRY_RUN')
              defaultValue(false)
              description('Skip deployment')
            }
            choiceParam {
              name('REGION')
              choices(['eu-west-1', 'us-east-1', 'ap-southeast-1'])
              description('AWS region')
            }
          }
        }
      }
    }
  }
}
```

### Apply parameters to specific branches only

```groovy
multibranchPipelineJob('my-service') {
  branchSources {
    branchSource {
      source {
        git {
          id('my-repo')
          remote('https://github.com/example/my-service.git')
        }
      }
      strategy {
        parameterizedBranchStrategy {
          filterMode('INCLUDE_PATTERN')
          branchPattern('main|develop|release/.*')
          parameterPolicy('REPLACE')
          parameterDefinitions {
            stringParam {
              name('DEPLOY_ENV')
              defaultValue('staging')
              description('Target environment')
            }
            booleanParam {
              name('DRY_RUN')
              defaultValue(false)
              description('Skip deployment')
            }
          }
        }
      }
    }
  }
}
```

Valid `filterMode` values: `ALL`, `INCLUDE_PATTERN`, `EXCLUDE_PATTERN`.  
Valid `parameterPolicy` values: `REPLACE`, `MERGE_PLUGIN_WINS`, `MERGE_JENKINSFILE_WINS`, `SKIP_IF_JENKINSFILE`.

---

## Building & Testing

```bash
# Run all tests
mvn test

# Start a local Jenkins with the plugin pre-installed
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
ParameterPolicy                       (enum: REPLACE / MERGE / SKIP_IF_JENKINSFILE)
MultiBranchHeaderParameter            (visual marker on Build with Parameters page)
config.jelly (×2)                     ← Jelly UI for Configure page
```

---

## Contributing

Pull requests are welcome. Please add tests for any new behaviour and run
`mvn checkstyle:check` before submitting.

---

## License

MIT
