package com.ppm.integration.agilesdk.connector.azuredevops.util;

import com.ppm.integration.agilesdk.connector.azuredevops.model.Field;
import com.ppm.integration.agilesdk.connector.azuredevops.model.WorkItem;
import com.ppm.integration.agilesdk.connector.azuredevops.service.AzureDevopsService;
import com.ppm.integration.agilesdk.dm.*;
import com.ppm.integration.agilesdk.model.AgileEntity;
import org.apache.commons.lang.StringUtils;

import java.util.List;

public class AgileEntityUtils {
    public static AgileEntity workItemToAgileEntity(WorkItem wi, AzureDevopsService service, List<Field> fieldsInfo) {

        if (wi == null) {
            return null;
        }

        AgileEntity ae = new AgileEntity();
        ae.setEntityUrl(service.getWorkItemUrl(wi.getId()));
        ae.setId(wi.getId());
        ae.setLastUpdateTime(wi.getLastUpdateTime());

        for (Field field : fieldsInfo) {
            ae.addField(field.getReferenceName(), getDataFieldFromField(wi, field, service));
        }

        return ae;
    }

    private static DataField getDataFieldFromField(WorkItem wi, Field field, AzureDevopsService service) {
        DataField.DATA_TYPE dataType = getAgileFieldType(field);

        DataField f;

        switch (dataType) {

            case USER:

                List<com.hp.ppm.user.model.User> users = wi.getResourceField(field.getReferenceName(), null, service.getUserProvider());
                if (users != null && users.size() == 1) {
                    f = new UserField();
                    com.ppm.integration.agilesdk.dm.User user = new com.ppm.integration.agilesdk.dm.User();
                    com.hp.ppm.user.model.User matchingUser = users.get(0);
                    user.setUserId(matchingUser.getUserId());
                    user.setEmail(matchingUser.getEmail());
                    user.setFullName(matchingUser.getFullName());
                    user.setUsername(matchingUser.getUserName());
                    f.set(user);
                    return f;
                } else {
                    return null;
                }
            case INTEGER:
            case FLOAT:
                f = new StringField();
                Double v = wi.getNumberField(field.getReferenceName());
                if (v != null) {
                    f.set(v.toString());
                }
                return f;
            default: // STRING, MEMO, and anything else.
                f = new StringField();
                f.set(wi.getStringField(field.getReferenceName()));
                return f;
        }
    }

    public static DataField.DATA_TYPE getAgileFieldType(Field field) {

        // Users are declared as type "String", so we can only identify them by reference name.
        if (!StringUtils.isBlank(field.getReferenceName()) &&
                (field.getReferenceName().startsWith("Microsoft.VSTS.CMMI.ActualAttendee")
                        || field.getReferenceName().startsWith("Microsoft.VSTS.CMMI.OptionalAttendee")
                        || field.getReferenceName().startsWith("Microsoft.VSTS.CMMI.RequiredAttendee")
                        || field.getReferenceName().startsWith("Microsoft.VSTS.CMMI.SubjectMatterExpert")
                        || "Microsoft.VSTS.CodeReview.AcceptedBy".equals(field.getReferenceName())
                        || "Microsoft.VSTS.Common.ActivatedBy".equals(field.getReferenceName())
                        || "System.AssignedTo".equals(field.getReferenceName())
                        || "Microsoft.VSTS.CodeReview.ContextOwner".equals(field.getReferenceName())
                        || "System.AuthorizedAs".equals(field.getReferenceName())
                        || "Microsoft.VSTS.CMMI.CalledBy".equals(field.getReferenceName())
                        || "System.ChangedBy".equals(field.getReferenceName())
                        || "Microsoft.VSTS.Common.ClosedBy".equals(field.getReferenceName())
                        || "System.CreatedBy".equals(field.getReferenceName())
                        || "Microsoft.VSTS.Common.ResolvedBy".equals(field.getReferenceName())
                        || "Microsoft.VSTS.Common.ReviewedBy".equals(field.getReferenceName())
                )) {
            return DataField.DATA_TYPE.USER;
        } else if ("integer".equals(field.getType()) || "picklistInteger".equals(field.getType())) {
            return DataField.DATA_TYPE.INTEGER;
        } else if ("double".equals(field.getType()) || "picklistDouble".equals(field.getType())) {
            return DataField.DATA_TYPE.FLOAT;
        } else if ("html".equals(field.getType())) {
            return DataField.DATA_TYPE.MEMO;
        } else {
            // Everything else is a String, including date fields.
            return DataField.DATA_TYPE.STRING;
        }
    }
}
