"""Independent PLATE/M03 OpenAPI subset checker used only by tests."""
from datetime import datetime, timezone
import math
import re
import uuid

def require(condition, message):
    if not condition:
        raise ValueError(message)

def instant(value):
    require(isinstance(value, str), "Time must be a string")
    require(re.fullmatch(r"\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d{1,6})?(?:Z|[+-]\d{2}:\d{2})",value) is not None,
            "Time must have a timezone and seconds")
    result = datetime.fromisoformat(value.replace("Z","+00:00"))
    require(result.utcoffset() is not None, "Missing timezone")
    return result.astimezone(timezone.utc)

def validate_dto(value, spec, schemas, path="record"):
    """Only the keyword subset used by the supplied PLATE and M03 DTOs; not a general OpenAPI validator."""
    if "$ref" in spec:
        return validate_dto(value, schemas[spec["$ref"].rsplit("/",1)[-1]], schemas, path)
    if value is None:
        require(spec.get("nullable",False), path + ": null forbidden")
        return
    kind=spec.get("type")
    if kind == "object":
        require(isinstance(value,dict),path + ": expected object")
        props=spec.get("properties",{})
        require(set(spec.get("required",[])) <= set(value),path + ": missing required fields")
        if spec.get("additionalProperties") is False:
            require(set(value) <= set(props),path + ": unexpected fields")
        for key,child in value.items():
            if key in props:
                validate_dto(child,props[key],schemas,path+"."+key)
    elif kind == "array":
        require(isinstance(value, list), path + ": expected array")
        if "minItems" in spec: require(len(value) >= spec["minItems"], path + ": too few items")
        if "maxItems" in spec: require(len(value) <= spec["maxItems"], path + ": too many items")
        for index, child in enumerate(value):
            validate_dto(child, spec["items"], schemas, path + "[" + str(index) + "]")
    elif kind == "string":
        require(isinstance(value,str),path + ": expected string")
        if spec.get("format") == "uuid":
            require(str(uuid.UUID(value)) == value,path+": noncanonical UUID")
        if spec.get("format") == "date-time":
            instant(value)
        if "minLength" in spec:
            require(len(value)>=spec["minLength"],path+": too short")
        if "maxLength" in spec:
            require(len(value)<=spec["maxLength"],path+": too long")
    elif kind == "boolean":
        require(type(value) is bool,path+": expected boolean")
    elif kind in ("integer","number"):
        require(type(value) in ((int,) if kind=="integer" else (int,float)),path+": expected number, not boolean")
        require(math.isfinite(value),path+": nonfinite")
        if "minimum" in spec: require(value>=spec["minimum"],path+": below minimum")
        if "maximum" in spec: require(value<=spec["maximum"],path+": above maximum")
    else:
        raise ValueError(path+": unsupported verification schema type "+str(kind))
    if "enum" in spec: require(value in spec["enum"],path+": bad enum")
    if "pattern" in spec: require(re.search(spec["pattern"],value) is not None,path+": bad pattern")
