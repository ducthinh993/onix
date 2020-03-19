/*
Onix Config Manager - Copyright (c) 2018-2020 by www.gatblau.org

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.

Contributors to this project, hereby assign copyright in their code to the
project, to be licensed under the same terms as the rest of the code.
*/

package org.gatblau.onix;

import org.gatblau.onix.data.*;
import org.gatblau.onix.security.Crypto;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.postgresql.jdbc.PgArray;
import org.postgresql.util.PGobject;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;

@Component
public class Lib implements InitializingBean {
    private DateFormat dateFormat = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss Z");
    private JSONParser jsonParser = new JSONParser();

    @Autowired
    private Crypto crypto;

    @Override
    public void afterPropertiesSet() throws Exception {
        dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
    }

    public JSONObject toJSON(Object value) throws ParseException, IOException {
        JSONObject json = null;
        if (value instanceof PGobject) {
            PGobject pgObj = (PGobject)value;
            String strValue = pgObj.getValue();
            json = (JSONObject)jsonParser.parse(strValue);
        }
        else if (value instanceof LinkedHashMap || value instanceof HashMap) {
            json = new JSONObject((HashMap)value);
        }
        else if (value instanceof String) {
            JSONParser parser = new JSONParser();
            StringReader reader = new StringReader((String)value);
            return (JSONObject)parser.parse(reader);
        } else if (value instanceof byte[]) {
            return toJSON(new String((byte[])value, StandardCharsets.UTF_8));
        }
        else {
            // the object is not a list, then create an empty JSON object
            json = new JSONObject();
            if (value != null) {
                System.out.println(String.format("WARNING: incorrect map format found on item '%s', discarding item content.", value));
            }
        }
        return json;
    }

    public String toJSONString(Object value) throws ParseException, IOException {
        return toJSON(value).toJSONString();
    }

    public String toArrayString(Object value) {
        String arrayString = null;
        if (value == null) {
            arrayString = toArrayString(new ArrayList<>());
        }
        try {
            arrayString = toArrayString((List<String>)value);
        }
        catch (Exception ex) {
            System.out.println(String.format("WARNING: incorrect array format found on item '%'.", value));
            ex.printStackTrace();
            List<String> list = new ArrayList<>();
            String tagStr = (String) value;
            String[] strs = tagStr.split("[ ]|[|]|[:]|[,]"); // valid tag separators are blank space, pipe, colon or comma
            for (String s : strs) {
                list.add(s);
            }
            arrayString = toArrayString(list);
        }
        return arrayString;
    }

    public String toArrayString(List<String> list) {
        if (list == null) {
            return "{}";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        for (int i = 0; i < list.size(); i++){
            sb.append("\"").append(list.get(i)).append("\"");
            if (i < list.size() - 1) {
                sb.append(",");
            }
        }
        sb.append("}");
        return sb.toString();
    }

    public List<String> toList(Object value) throws SQLException {
        if (value == null) {
            return new ArrayList<>();
        }
        else if (value instanceof PgArray) {
            PgArray pgArray = (PgArray) value;
            String[] array = (String[])pgArray.getArray();
            return Arrays.asList(array);
        }
        throw new RuntimeException("Conversion not implemented.");
    }

    public ItemData toItemData(ResultSet set) throws SQLException, ParseException, IOException {
        ItemData item = null;
        if (set != null) {
            Date updated = set.getDate("updated");
            String partitionKey = null;
            try {
                set.findColumn("partition_key");
                partitionKey = set.getString("partition_key");
            } catch (SQLException e) { }
            item = new ItemData();
            item.setKey(set.getString("key"));
            item.setName(set.getString("name"));
            item.setDescription(set.getString("description"));
            item.setStatus(set.getShort("status"));
            item.setType(set.getString("item_type_key"));
            item.setCreated(dateFormat.format(set.getDate("created")));
            item.setUpdated((updated != null) ? dateFormat.format(updated) : null);
            item.setMeta(toJSON(set.getObject("meta")));
            item.setMetaEnc(set.getBoolean("meta_enc"));
            item.setTag(toList(set.getObject("tag")));
            item.setVersion(set.getInt("version"));
            item.setAttribute(toJSON(set.getObject("attribute")));
            item.setChangedBy(set.getString("changed_by"));
            item.setPartition(partitionKey);
            item.setTxt(set.getString("txt"));
            item.setTxtEnc(set.getBoolean("txt_enc"));
            item.setEncKeyIx(set.getShort("enc_key_ix"));
        }
        return item;
    }

    public LinkData toLinkData(ResultSet set) throws SQLException, ParseException, IOException {
        LinkData link = null;
        if (set != null) {
            Date updated = set.getDate("updated");
            link = new LinkData();
            link.setMetaEnc(set.getBoolean("meta_enc"));
            link.setTxtEnc(set.getBoolean("txt_enc"));
            link.setEncKeyIx(set.getShort("enc_key_ix"));
            link.setKey(set.getString("key"));
            link.setType(set.getString("link_type_key"));
            link.setDescription(set.getString("description"));
            link.setEndItemKey(set.getString("end_item_key"));
            link.setStartItemKey(set.getString("start_item_key"));
            link.setMeta(toJSON(set.getObject("meta")));
            link.setTxt(set.getString("txt"));
            link.setTag(toList(set.getObject("tag")));
            link.setAttribute(toJSON(set.getObject("attribute")));
            link.setUpdated((updated != null) ? dateFormat.format(updated) : null);
            link.setVersion(set.getInt("version"));
            link.setChangedBy(set.getString("changed_by"));
        }
        return link;
    }

    public TypeAttrData toTypeAttrData(ResultSet set) throws SQLException, ParseException, IOException {
        TypeAttrData typeAttr = null;
        if (set != null) {
            Date updated = set.getDate("updated");
            typeAttr = new TypeAttrData();
            typeAttr.setKey(set.getString("key"));
            typeAttr.setName(set.getString("name"));
            typeAttr.setDescription(set.getString("description"));
            typeAttr.setType(set.getString("type"));
            typeAttr.setDefValue(set.getString("def_value"));
            typeAttr.setManaged(set.getBoolean("managed"));
            typeAttr.setRequired(set.getBoolean("required"));
            typeAttr.setRegex(set.getString("regex"));
            typeAttr.setCreated(dateFormat.format(set.getDate("created")));
            typeAttr.setUpdated((updated != null) ? dateFormat.format(updated) : null);
            typeAttr.setVersion(set.getInt("version"));
            typeAttr.setChangedBy(set.getString("changed_by"));
            typeAttr.setManaged(set.getBoolean("managed"));
        }
        return typeAttr;
    }

    public ItemTypeData toItemTypeData(ResultSet set) throws SQLException, ParseException, IOException {
        ItemTypeData itemType = null;
        if (set != null) {
            Date updated = set.getDate("updated");
            itemType = new ItemTypeData();
            itemType.setKey(set.getString("key"));
            itemType.setName(set.getString("name"));
            itemType.setDescription(set.getString("description"));
            itemType.setCreated(dateFormat.format(set.getDate("created")));
            itemType.setUpdated((updated != null) ? dateFormat.format(updated) : null);
            itemType.setVersion(set.getInt("version"));
            itemType.setFilter(toJSON(set.getObject("filter")));
            itemType.setMetaSchema(toJSON(set.getObject("meta_schema")));
            itemType.setModelKey(set.getString("model_key"));
            itemType.setChangedBy(set.getString("changed_by"));
            itemType.setRoot(set.getBoolean("root"));
            itemType.setNotifyChange(set.getBoolean("notify_change"));
            itemType.setTag(toList(set.getObject("tag")));
            itemType.setEncryptMeta(set.getBoolean("encrypt_meta"));
            itemType.setEncryptTxt(set.getBoolean("encrypt_txt"));
            itemType.setManaged(set.getBoolean("managed"));
        }
        return itemType;
    }

    public String toHStoreString(Map<String, String> map) {
        String result = null;
        if (map == null) {
            result = null;
        }
        else {
            StringBuilder sb = new StringBuilder();
            int count = 0;
            for (Map.Entry<String, String> entry : map.entrySet()) {
                sb.append(entry.getKey()).append("=>").append(entry.getValue());
                if (count < map.entrySet().size() - 1) {
                    sb.append(",");
                }
                count++;
            }
            result = sb.toString();
        }
        return result;
    }

    public LinkTypeData toLinkTypeData(ResultSet set) throws SQLException, ParseException, IOException {
        LinkTypeData linkType = null;
        if (set != null) {
            Date updated = set.getDate("updated");
            linkType = new LinkTypeData();
            linkType.setKey(set.getString("key"));
            linkType.setName(set.getString("name"));
            linkType.setDescription(set.getString("description"));
            linkType.setCreated(dateFormat.format(set.getDate("created")));
            linkType.setUpdated((updated != null) ? dateFormat.format(updated) : null);
            linkType.setVersion(set.getInt("version"));
            linkType.setMetaSchema(toJSON(set.getObject("meta_schema")));
            linkType.setTag(toList(set.getObject("tag")));
            linkType.setEncryptMeta(set.getBoolean("encrypt_meta"));
            linkType.setEncryptTxt(set.getBoolean("encrypt_txt"));
            linkType.setManaged(set.getBoolean("managed"));
            linkType.setModelKey(set.getString("model_key"));
            linkType.setChangedBy(set.getString("changed_by"));
        }
        return linkType;
    }

    public LinkRuleData toLinkRuleData(ResultSet set) throws SQLException {
        LinkRuleData linkRule = null;
        if (set != null) {
            linkRule = new LinkRuleData();
            Date updated = set.getDate("updated");
            linkRule.setKey(set.getString("key"));
            linkRule.setName(set.getString("name"));
            linkRule.setDescription(set.getString("description"));
            linkRule.setLinkTypeKey(set.getString("link_type_key"));
            linkRule.setStartItemTypeKey(set.getString("start_item_type_key"));
            linkRule.setEndItemTypeKey(set.getString("end_item_type_key"));
            linkRule.setCreated(dateFormat.format(set.getDate("created")));
            linkRule.setUpdated((updated != null) ? dateFormat.format(updated) : null);
            linkRule.setVersion(set.getInt("version"));
            linkRule.setChangedBy(set.getString("changed_by"));
        }
        return linkRule;
    }

    public TagData toTagData(ResultSet set) throws SQLException {
        TagData tag = null;
        if (set != null) {
            Date updated = set.getDate("updated");
            tag = new TagData();
            tag.setLabel(set.getString("label"));
            tag.setName(set.getString("name"));
            tag.setDescription(set.getString("description"));
            tag.setRootItemKey(set.getString("root_item_key"));
            tag.setCreated(dateFormat.format(set.getDate("created")));
            tag.setUpdated((updated != null) ? dateFormat.format(updated) : null);
            tag.setVersion(set.getInt("version"));
            tag.setChangedBy(set.getString("changed_by"));
        }
        return tag;
    }

    public ModelData toModelData(ResultSet set) throws SQLException {
        ModelData model = null;
        if (set != null) {
            Date updated = set.getDate("updated");
            model = new ModelData();
            model.setKey(set.getString("key"));
            model.setName(set.getString("name"));
            model.setDescription(set.getString("description"));
            model.setCreated(dateFormat.format(set.getDate("created")));
            model.setUpdated((updated != null) ? dateFormat.format(updated) : null);
            model.setVersion(set.getInt("version"));
            model.setChangedBy(set.getString("changed_by"));
            model.setManaged(set.getBoolean("managed"));
        }
        return model;
    }

    public PartitionData toPartitionData(ResultSet set) throws SQLException {
        PartitionData part = null;
        if (set != null) {
            Date updated = set.getDate("updated");
            part = new PartitionData();
            part.setKey(set.getString("key"));
            part.setName(set.getString("name"));
            part.setDescription(set.getString("description"));
            part.setCreated(dateFormat.format(set.getDate("created")));
            part.setUpdated((updated != null) ? dateFormat.format(updated) : null);
            part.setVersion(set.getInt("version"));
            part.setChangedBy(set.getString("changed_by"));
        }
        return part;
    }

    public RoleData toRoleData(ResultSet set) throws SQLException {
        RoleData role = null;
        if (set != null) {
            Date updated = set.getDate("updated");
            role = new RoleData();
            role.setKey(set.getString("key"));
            role.setName(set.getString("name"));
            role.setDescription(set.getString("description"));
            role.setCreated(dateFormat.format(set.getDate("created")));
            role.setUpdated((updated != null) ? dateFormat.format(updated) : null);
            role.setVersion(set.getInt("version"));
            role.setChangedBy(set.getString("changed_by"));
        }
        return role;
    }

    public PrivilegeData toPrivilegeData(ResultSet set) throws SQLException {
        PrivilegeData priv = null;
        if (set != null) {
            priv = new PrivilegeData();
            priv.setKey(set.getString("key"));
            priv.setRoleKey(set.getString("role_key"));
            priv.setPartitionKey(set.getString("partition_key"));
            priv.setCanCreate(set.getBoolean("can_create"));
            priv.setCanRead(set.getBoolean("can_read"));
            priv.setCanDelete(set.getBoolean("can_delete"));
            priv.setChangedBy(set.getString("changed_by"));
            priv.setCreated(dateFormat.format(set.getDate("created")));
        }
        return priv;
    }

    public byte[] encryptTxt(String txt) {
        return crypto.encrypt(txt);
    }

    public byte[] decryptTxt(byte[] encryptedTxt, short keyIx) {
        return crypto.decrypt(encryptedTxt, keyIx);
    }

    public String toString(InputStream inputStream) {
        String str;
        try {
            ByteArrayOutputStream result = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int length;
            while ((length = inputStream.read(buffer)) != -1) {
                result.write(buffer, 0, length);
            }
            str = result.toString(StandardCharsets.UTF_8.name());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return str;
    }

    public byte[] toByteArray(InputStream inputStream) {
        try {
            ByteArrayOutputStream result = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int length;
            while ((length = inputStream.read(buffer)) != -1) {
                result.write(buffer, 0, length);
            }
            return result.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public String wrapJSON(String value) {
        return  String.format("{\"cipher\": \"%s\"}", value);
    }

    public String unwrapJSON(JSONObject value) {
        return value.get("cipher").toString();
    }

    public Short getEncKeyIx() {
        return crypto.getKeyIx();
    }

    public short getDefaultEncKeyIx() {
        return crypto.getDefaultKeyIx();
    }

    public short getAlternateKeyIx() {
       return (getDefaultEncKeyIx() == 1 ? (short)2 : 1);
    }

    public String getDefaultEncKeyExpiry() {
        return crypto.getDefaultKeyExpiry();
    }
}
