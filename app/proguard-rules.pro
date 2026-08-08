# R8 is disabled for Milestone 1 (see AndroidApplicationConventionPlugin). This file exists so
# the release build type resolves, and is where keep rules land during the Milestone 8
# optimization pass.
#
# Expected additions at that point:
#   - kotlinx.serialization @Serializable navigation route classes
#   - Room entities and generated DAO implementations
#   - Glance / RemoteViews classes referenced reflectively by the widget host
