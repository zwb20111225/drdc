#ifndef __KSU_H_SEPOLICY
#define __KSU_H_SEPOLICY

#include <linux/types.h>

#include "ss/policydb.h"

struct selinux_policy *drdc_dup_sepolicy(struct selinux_policy *old_pol);

void drdc_destroy_sepolicy(struct selinux_policy *orig);

// Operation on types
bool drdc_type(struct policydb *db, const char *name, const char *attr);
bool drdc_attribute(struct policydb *db, const char *name);
bool drdc_permissive(struct policydb *db, const char *type);
bool drdc_enforce(struct policydb *db, const char *type);
bool drdc_typeattribute(struct policydb *db, const char *type, const char *attr);
bool drdc_exists(struct policydb *db, const char *type);

// Access vector rules
bool drdc_allow(struct policydb *db, const char *src, const char *tgt, const char *cls, const char *perm);
bool drdc_deny(struct policydb *db, const char *src, const char *tgt, const char *cls, const char *perm);
bool drdc_auditallow(struct policydb *db, const char *src, const char *tgt, const char *cls, const char *perm);
bool drdc_dontaudit(struct policydb *db, const char *src, const char *tgt, const char *cls, const char *perm);

// Extended permissions access vector rules
bool drdc_allowxperm(struct policydb *db, const char *src, const char *tgt, const char *cls, const char *range);
bool drdc_auditallowxperm(struct policydb *db, const char *src, const char *tgt, const char *cls, const char *range);
bool drdc_dontauditxperm(struct policydb *db, const char *src, const char *tgt, const char *cls, const char *range);

// Type rules
bool drdc_type_transition(struct policydb *db, const char *src, const char *tgt, const char *cls, const char *def,
                          const char *obj);
bool drdc_type_change(struct policydb *db, const char *src, const char *tgt, const char *cls, const char *def);
bool drdc_type_member(struct policydb *db, const char *src, const char *tgt, const char *cls, const char *def);

// File system labeling
bool drdc_genfscon(struct policydb *db, const char *fs_name, const char *path, const char *ctx);

#endif
