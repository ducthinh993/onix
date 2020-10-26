/*
  this portion has been taken from the https://github.com/docker/distribution project
  that is licensed under the Apache 2.0 license, and modified to suit this project
*/
package core

// represent any way of referencing artefacts within the directory.
// Its main purpose is to abstract tags and digests (content-addressable hash).
//
// Grammar
//
// 	reference                       := name [ ":" tag ] [ "@" digest ]
//	name                            := [domain '/'] path-component ['/' path-component]*
//	domain                          := domain-component ['.' domain-component]* [':' port-number]
//	domain-component                := /([a-zA-Z0-9]|[a-zA-Z0-9][a-zA-Z0-9-]*[a-zA-Z0-9])/
//	port-number                     := /[0-9]+/
//	path-component                  := alpha-numeric [separator alpha-numeric]*
// 	alpha-numeric                   := /[a-z0-9]+/
//	separator                       := /[_.]|__|[-]*/
//
//	tag                             := /[\w][\w.-]{0,127}/
//
//	digest                          := digest-algorithm ":" digest-hex
//	digest-algorithm                := digest-algorithm-component [ digest-algorithm-separator digest-algorithm-component ]*
//	digest-algorithm-separator      := /[+.-_]/
//	digest-algorithm-component      := /[A-Za-z][A-Za-z0-9]*/
//	digest-hex                      := /[0-9a-fA-F]{32,}/ ; At least 128 bit digest value
//
//	identifier                      := /[a-f0-9]{64}/
//	short-identifier                := /[a-f0-9]{6,64}/

import (
	"errors"
	"fmt"
	"github.com/opencontainers/go-digest"
	"strings"
)

const (
	// NameTotalLengthMax is the maximum total number of characters in a repository name.
	NameTotalLengthMax = 255
)

var (
	defaultDomain    = "artie.directory"
	officialRepoName = "library"
	defaultTag       = "latest"

	// ErrReferenceInvalidFormat represents an error while trying to parse a string as a reference.
	ErrReferenceInvalidFormat = errors.New("invalid reference format")

	// ErrTagInvalidFormat represents an error while trying to parse a string as a tag.
	ErrTagInvalidFormat = errors.New("invalid tag format")

	// ErrDigestInvalidFormat represents an error while trying to parse a string as a tag.
	ErrDigestInvalidFormat = errors.New("invalid digest format")

	// ErrNameContainsUppercase is returned for invalid repository names that contain uppercase characters.
	ErrNameContainsUppercase = errors.New("repository name must be lowercase")

	// ErrNameEmpty is returned for empty, invalid repository names.
	ErrNameEmpty = errors.New("repository name must have at least one component")

	// ErrNameTooLong is returned when a repository name is longer than NameTotalLengthMax.
	ErrNameTooLong = fmt.Errorf("repository name must not be more than %v characters", NameTotalLengthMax)

	// ErrNameNotCanonical is returned when a name is not canonical.
	ErrNameNotCanonical = errors.New("repository name must be canonical")
)

// Reference is an opaque object reference identifier that may include
// modifiers such as a hostname, name, tag, and digest.
type Reference interface {
	// String returns the full reference
	String() string
}

// Field provides a wrapper type for resolving correct reference types when
// working with encoding.
type Field struct {
	reference Reference
}

// AsField wraps a reference in a Field for encoding.
func AsField(reference Reference) Field {
	return Field{reference}
}

// Reference unwraps the reference type from the field to
// return the Reference object. This object should be
// of the appropriate type to further check for different
// reference types.
func (f Field) Reference() Reference {
	return f.reference
}

// MarshalText serializes the field to byte text which
// is the string of the reference.
func (f Field) MarshalText() (p []byte, err error) {
	return []byte(f.reference.String()), nil
}

// UnmarshalText parses text bytes by invoking the
// reference parser to ensure the appropriately
// typed reference object is wrapped by field.
func (f *Field) UnmarshalText(p []byte) error {
	r, err := Parse(string(p))
	if err != nil {
		return err
	}

	f.reference = r
	return nil
}

// Named is an object with a full name
type Named interface {
	Reference
	Name() string
}

// Tagged is an object which has a tag
type Tagged interface {
	Reference
	Tag() string
}

// NamedTagged is an object including a name and tag.
type NamedTagged interface {
	Named
	Tag() string
}

// Digested is an object which has a digest
// in which it can be referenced by
type Digested interface {
	Reference
	Digest() digest.Digest
}

// Canonical reference is an object with a fully unique
// name including a name with domain and digest
type Canonical interface {
	Named
	Digest() digest.Digest
}

// namedRepository is a reference to a repository with a name.
// A namedRepository has both domain and path components.
type namedRepository interface {
	Named
	Domain() string
	Path() string
}

// Domain returns the domain part of the Named reference
func Domain(named Named) string {
	if r, ok := named.(namedRepository); ok {
		return r.Domain()
	}
	domain, _ := splitDomain(named.Name())
	return domain
}

// Path returns the name without the domain part of the Named reference
func Path(named Named) (name string) {
	if r, ok := named.(namedRepository); ok {
		return r.Path()
	}
	_, path := splitDomain(named.Name())
	return path
}

func splitDomain(name string) (string, string) {
	match := anchoredNameRegexp.FindStringSubmatch(name)
	if len(match) != 3 {
		return "", name
	}
	return match[1], match[2]
}

// Parse parses s and returns a syntactically valid Reference.
// If an error was encountered it is returned, along with a nil Reference.
// NOTE: Parse will not handle short digests.
func Parse(s string) (Reference, error) {
	matches := ReferenceRegexp.FindStringSubmatch(s)
	if matches == nil {
		if s == "" {
			return nil, ErrNameEmpty
		}
		if ReferenceRegexp.FindStringSubmatch(strings.ToLower(s)) != nil {
			return nil, ErrNameContainsUppercase
		}
		return nil, ErrReferenceInvalidFormat
	}

	if len(matches[1]) > NameTotalLengthMax {
		return nil, ErrNameTooLong
	}

	var repo repository

	nameMatch := anchoredNameRegexp.FindStringSubmatch(matches[1])
	if len(nameMatch) == 3 {
		repo.domain = nameMatch[1]
		repo.path = nameMatch[2]
	} else {
		repo.domain = ""
		repo.path = matches[1]
	}

	ref := reference{
		namedRepository: repo,
		tag:             matches[2],
	}
	if matches[3] != "" {
		var err error
		ref.digest, err = digest.Parse(matches[3])
		if err != nil {
			return nil, err
		}
	}

	r := getBestReferenceType(ref)
	if r == nil {
		return nil, ErrNameEmpty
	}

	return r, nil
}

func getBestReferenceType(ref reference) Reference {
	if ref.Name() == "" {
		// Allow digest only references
		if ref.digest != "" {
			return digestReference(ref.digest)
		}
		return nil
	}
	if ref.tag == "" {
		if ref.digest != "" {
			return canonicalReference{
				namedRepository: ref.namedRepository,
				digest:          ref.digest,
			}
		}
		return ref.namedRepository
	}
	if ref.digest == "" {
		return taggedReference{
			namedRepository: ref.namedRepository,
			tag:             ref.tag,
		}
	}

	return ref
}

type reference struct {
	namedRepository
	tag    string
	digest digest.Digest
}

func (r reference) String() string {
	return r.Name() + ":" + r.tag + "@" + r.digest.String()
}

func (r reference) Tag() string {
	return r.tag
}

func (r reference) Digest() digest.Digest {
	return r.digest
}

type repository struct {
	domain string
	path   string
}

func (r repository) String() string {
	return r.Name()
}

func (r repository) Name() string {
	if r.domain == "" {
		return r.path
	}
	return r.domain + "/" + r.path
}

func (r repository) Domain() string {
	return r.domain
}

func (r repository) Path() string {
	return r.path
}

type digestReference digest.Digest

func (d digestReference) String() string {
	return digest.Digest(d).String()
}

func (d digestReference) Digest() digest.Digest {
	return digest.Digest(d)
}

type taggedReference struct {
	namedRepository
	tag string
}

func (t taggedReference) String() string {
	return t.Name() + ":" + t.tag
}

func (t taggedReference) Tag() string {
	return t.tag
}

type canonicalReference struct {
	namedRepository
	digest digest.Digest
}

func (c canonicalReference) String() string {
	return c.Name() + "@" + c.digest.String()
}

func (c canonicalReference) Digest() digest.Digest {
	return c.digest
}

// normalizedNamed represents a name which has been
// normalized and has a familiar form. A familiar name
// is what is used in Docker UI. An example normalized
// name is "docker.io/library/ubuntu" and corresponding
// familiar name of "ubuntu".
type normalizedNamed interface {
	Named
	Familiar() Named
}

// ParseNormalizedNamed parses a string into a named reference
// transforming a familiar name from Docker UI to a fully
// qualified reference. If the value may be an identifier
// use ParseAnyReference.
func ParseNormalizedNamed(s string) (Named, error) {
	if ok := anchoredIdentifierRegexp.MatchString(s); ok {
		return nil, fmt.Errorf("invalid repository name (%s), cannot specify 64-byte hexadecimal strings", s)
	}
	domain, remainder := splitDockerDomain(s)
	var remoteName string
	if tagSep := strings.IndexRune(remainder, ':'); tagSep > -1 {
		remoteName = remainder[:tagSep]
	} else {
		remoteName = remainder
	}
	if strings.ToLower(remoteName) != remoteName {
		return nil, errors.New("invalid reference format: repository name must be lowercase")
	}

	ref, err := Parse(domain + "/" + remainder)
	if err != nil {
		return nil, err
	}
	named, isNamed := ref.(Named)
	if !isNamed {
		return nil, fmt.Errorf("reference %s has no name", ref.String())
	}
	return named, nil
}

// splitDockerDomain splits a repository name to domain and remotename string.
// If no valid domain is found, the default domain is used. Repository name
// needs to be already validated before.
func splitDockerDomain(name string) (domain, remainder string) {
	i := strings.IndexRune(name, '/')
	if i == -1 || (!strings.ContainsAny(name[:i], ".:") && name[:i] != "localhost") {
		domain, remainder = defaultDomain, name
	} else {
		domain, remainder = name[:i], name[i+1:]
	}
	if domain == defaultDomain && !strings.ContainsRune(remainder, '/') {
		remainder = officialRepoName + "/" + remainder
	}
	return
}